package app.klean.feature.calendar.data

import android.util.Log
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.db.transact
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.ical.ICalCodec
import app.klean.shared.id.UuidV7
import app.klean.shared.id.nameUuid
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** What the editor, capture and ICS import hand the repository. Times are UTC ms; [zone] is the IANA zone. */
data class EventDraft(
    val title: String,
    val startAt: Long,
    val endAt: Long,
    val allDay: Boolean = false,
    val zone: String,
    val location: String? = null,
    val notes: String? = null,
    /** RRULE value without the `RRULE:` prefix; null = once. */
    val rrule: String? = null,
    /** Minutes before the start for a quiet reminder; null = none. */
    val reminderMinutes: Int? = null,
    val alarm: Boolean = false,
    /** iCalendar UID to keep (ICS import); null = the new row's id. */
    val uid: String? = null,
)

data class ImportResult(val added: Int, val updated: Int)

/** A calendar on this phone (CalendarContract.Calendars), as Klean lists it. */
data class DeviceCalendar(val id: Long, val name: String, val color: Int?, val accountName: String?)

object SourceKind {
    const val KLEAN = "KLEAN"
    const val DEVICE = "DEVICE"
    const val GOOGLE = "GOOGLE"
    const val DAV = "DAV"
}

/** EXDATE list as stored in `event.exdates`: a JSON array of UTC ms. */
object Exdates {
    private val serializer = ListSerializer(Long.serializer())
    fun decode(json: String?): List<Long> =
        if (json.isNullOrBlank()) emptyList() else runCatching { Json.decodeFromString(serializer, json) }.getOrDefault(emptyList())
    fun encode(list: List<Long>): String = Json.encodeToString(serializer, list.distinct().sorted())
}

/**
 * Told after every event write, outside the transaction: the provider mirror, event reminders and the
 * holistic surfaces follow. One listener failing never stops the others.
 */
fun interface EventChangeListener {
    suspend fun onEventsChanged(ids: List<String>)
}

/** Klean's events (Room is the source of truth) and the calendars shown next to them. */
interface CalendarRepository {
    /** Every live event, Klean's and imported. */
    fun observeEvents(): Flow<List<EventEntity>>
    suspend fun liveEvents(): List<EventEntity>
    suspend fun get(id: String): EventEntity?
    /** Saves a new event and returns its id. Throws on a blank title. */
    suspend fun add(draft: EventDraft): String
    suspend fun update(id: String, draft: EventDraft)
    /** Tombstones the event; it keeps syncing as deleted. */
    suspend fun delete(id: String)
    /** Leaves out one occurrence of a recurring event (an EXDATE). */
    suspend fun skipOccurrence(id: String, occurrenceStart: Long)
    /** Adds or updates (by UID) every VEVENT in [text]. */
    suspend fun importIcs(text: String): ImportResult
    /** Klean's own events as one iCalendar file. */
    suspend fun exportIcs(): String
    suspend fun exportCount(): Int

    fun observeSources(): Flow<List<CalendarSourceEntity>>
    suspend fun setSourceSelected(id: String, selected: Boolean)
    /** One `calendar_source` row per phone calendar; rows are written only when something changed. */
    suspend fun syncDeviceSources(calendars: List<DeviceCalendar>)
    /** The `calendar_source` id of this phone's calendar [calendarId]. Stable per device. */
    suspend fun deviceSourceId(calendarId: Long): String
    suspend fun ensureKleanSource(): CalendarSourceEntity
}

@Singleton
class DefaultCalendarRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val prefs: DevicePrefs,
    private val listeners: Set<@JvmSuppressWildcards EventChangeListener>,
) : CalendarRepository {
    private val events get() = db.eventDao()
    private val sources get() = db.calendarSourceDao()

    override fun observeEvents(): Flow<List<EventEntity>> = events.observeAll()
    override suspend fun liveEvents(): List<EventEntity> = events.observeAll().first()
    override suspend fun get(id: String): EventEntity? = events.get(id)

    override suspend fun add(draft: EventDraft): String {
        val clean = draft.cleaned()
        val id = db.transact {
            val rowId = UuidV7.generate(clock.now().toEpochMilliseconds())
            events.upsert(clean.toEntity(rowId, uid = clean.uid ?: rowId, exdates = "[]", stamp = stamper.stamp()))
            rowId
        }
        notify(listOf(id))
        return id
    }

    override suspend fun update(id: String, draft: EventDraft) {
        val clean = draft.cleaned()
        val found = db.transact {
            val prev = events.get(id)?.takeUnless { it.sync.deleted } ?: return@transact false
            events.upsert(
                clean.toEntity(id, uid = prev.uid ?: id, exdates = prev.exdates, stamp = stamper.stamp(prev.sync)).copy(
                    calendarId = prev.calendarId, remoteEtag = prev.remoteEtag, providerEventId = prev.providerEventId,
                ),
            )
            true
        }
        if (found) notify(listOf(id))
    }

    override suspend fun delete(id: String) = change(id) { it.copy(sync = stamper.stamp(it.sync, deleted = true)) }

    override suspend fun skipOccurrence(id: String, occurrenceStart: Long) = change(id) {
        it.copy(exdates = Exdates.encode(Exdates.decode(it.exdates) + occurrenceStart), sync = stamper.stamp(it.sync))
    }

    private suspend fun change(id: String, edit: suspend (EventEntity) -> EventEntity) {
        val found = db.transact {
            val prev = events.get(id)?.takeUnless { it.sync.deleted } ?: return@transact false
            events.upsert(edit(prev))
            true
        }
        if (found) notify(listOf(id))
    }

    override suspend fun importIcs(text: String): ImportResult {
        val tz = zone.current()
        val parsed = ICalCodec.parse(text)
        var added = 0
        var updated = 0
        val ids = db.transact {
            val byUid = events.observeAll().first().associateBy { it.uid }
            parsed.mapNotNull { ical ->
                val draft = EventIcs.toDraft(ical, tz).takeIf { it.title.isNotBlank() } ?: return@mapNotNull null
                val exdates = Exdates.encode(EventIcs.exdates(ical, tz))
                val prev = byUid[ical.uid]
                if (prev != null) {
                    updated++
                    events.upsert(draft.toEntity(prev.id, ical.uid, exdates, stamper.stamp(prev.sync)).copy(calendarId = prev.calendarId))
                    prev.id
                } else {
                    added++
                    val rowId = UuidV7.generate(clock.now().toEpochMilliseconds())
                    events.upsert(draft.toEntity(rowId, ical.uid, exdates, stamper.stamp()))
                    rowId
                }
            }
        }
        if (ids.isNotEmpty()) notify(ids)
        return ImportResult(added, updated)
    }

    override suspend fun exportIcs(): String = ICalCodec.write(kleanEvents().map { EventIcs.toICal(it) })

    override suspend fun exportCount(): Int = kleanEvents().size

    private suspend fun kleanEvents() = liveEvents().filter { it.calendarId == null }

    override fun observeSources(): Flow<List<CalendarSourceEntity>> = sources.observeAll()

    override suspend fun setSourceSelected(id: String, selected: Boolean) {
        db.transact {
            val prev = sources.get(id) ?: return@transact
            if (prev.selected != selected) sources.upsert(prev.copy(selected = selected, sync = stamper.stamp(prev.sync)))
        }
        notify(emptyList())
    }

    override suspend fun deviceSourceId(calendarId: Long): String = nameUuid("calendar.source.device:${prefs.deviceId()}:$calendarId")

    override suspend fun syncDeviceSources(calendars: List<DeviceCalendar>) {
        val ids = calendars.associateWith { deviceSourceId(it.id) }
        db.transact {
            for ((cal, id) in ids) {
                val prev = sources.get(id)
                val wanted = CalendarSourceEntity(
                    id = id, kind = SourceKind.DEVICE, name = cal.name, color = cal.color, remoteId = cal.id.toString(),
                    readOnly = true, selected = prev?.takeUnless { it.sync.deleted }?.selected ?: true,
                    sync = prev?.sync ?: stamper.stamp(),
                )
                if (prev == null || prev.sync.deleted || prev.copy(sync = wanted.sync) != wanted) {
                    sources.upsert(wanted.copy(sync = if (prev == null) wanted.sync else stamper.stamp(prev.sync, deleted = false)))
                }
            }
        }
    }

    override suspend fun ensureKleanSource(): CalendarSourceEntity {
        val id = nameUuid(KLEAN_SOURCE_KEY)
        return db.transact {
            sources.get(id)?.takeUnless { it.sync.deleted }
                ?: CalendarSourceEntity(id, SourceKind.KLEAN, "Klean", sync = stamper.stamp(sources.get(id)?.sync, deleted = false))
                    .also { sources.upsert(it) }
        }
    }

    private suspend fun notify(ids: List<String>) {
        for (l in listeners) {
            try {
                l.onEventsChanged(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "event listener failed: ${l::class.java.simpleName}", e)
            }
        }
    }

    private fun EventDraft.cleaned(): EventDraft {
        val t = title.trim()
        require(t.isNotEmpty()) { "An event needs a title" }
        return copy(
            title = t,
            endAt = if (endAt < startAt) startAt else endAt,
            location = location?.trim()?.ifEmpty { null },
            notes = notes?.trim()?.ifEmpty { null },
            rrule = rrule?.trim()?.removePrefix("RRULE:")?.ifEmpty { null },
        )
    }

    private fun EventDraft.toEntity(id: String, uid: String, exdates: String, stamp: app.klean.core.data.db.entity.SyncColumns) =
        EventEntity(
            id = id, title = title, startAt = startAt, endAt = endAt, allDay = allDay, zone = zone, location = location,
            notes = notes, recurrence = rrule, reminderMinutes = reminderMinutes, uid = uid, rrule = rrule,
            exdates = exdates, alarm = alarm, sync = stamp,
        )

    companion object {
        /** Name-based, so every device agrees on the id of Klean's own calendar. */
        const val KLEAN_SOURCE_KEY = "calendar.source.klean"
    }
}
