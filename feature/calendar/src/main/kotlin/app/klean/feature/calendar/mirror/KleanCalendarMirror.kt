package app.klean.feature.calendar.mirror

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import app.klean.core.data.account.KleanAccount
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.notifications.Reschedulable
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventChangeListener
import app.klean.feature.calendar.data.Exdates
import app.klean.shared.ical.ICalTime
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The provider calls the mirror needs, as plain maps so the mirror is tested without a ContentResolver. */
interface CalendarProviderOps {
    /** WRITE_CALENDAR is granted. */
    fun canWrite(): Boolean
    fun findKleanCalendar(): Long?
    fun createCalendar(values: Map<String, Any?>): Long
    fun deleteCalendar(id: Long)
    /** `_SYNC_ID` (Klean event id) → provider `_ID` for the rows of [calendarId]. */
    fun syncIds(calendarId: Long): Map<String, Long>
    fun insertEvent(values: Map<String, Any?>): Long
    fun updateEvent(id: Long, values: Map<String, Any?>)
    fun deleteEvent(id: Long)
}

/** The rows the mirror writes. Pure, so the values are checked in unit tests. */
object MirrorValues {
    /** Pastel lavender, the Klean accent. */
    const val COLOR: Int = 0xFFB9A7E8.toInt()

    fun calendar(accountName: String, accountType: String, zone: String): Map<String, Any?> = mapOf(
        Calendars.ACCOUNT_NAME to accountName,
        Calendars.ACCOUNT_TYPE to accountType,
        Calendars.NAME to "klean",
        Calendars.CALENDAR_DISPLAY_NAME to "Klean",
        Calendars.CALENDAR_COLOR to COLOR,
        // Read-only elsewhere: Klean is the source of truth and nothing flows back from the provider.
        Calendars.CALENDAR_ACCESS_LEVEL to Calendars.CAL_ACCESS_READ,
        Calendars.OWNER_ACCOUNT to accountName,
        Calendars.VISIBLE to 1,
        Calendars.SYNC_EVENTS to 1,
        Calendars.CALENDAR_TIME_ZONE to zone,
    )

    fun event(e: EventEntity, calendarId: Long): Map<String, Any?> {
        val zone = runCatching { TimeZone.of(e.zone) }.getOrDefault(TimeZone.UTC)
        // The provider wants all-day events at UTC midnight in the UTC zone.
        fun utcMidnight(ms: Long): Long =
            Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val start = if (e.allDay) utcMidnight(e.startAt) else e.startAt
        val end = if (e.allDay) utcMidnight(e.endAt) else e.endAt
        val recurring = e.rrule != null
        val exdates = Exdates.decode(e.exdates)
        return mapOf(
            Events.CALENDAR_ID to calendarId,
            Events._SYNC_ID to e.id,
            Events.TITLE to e.title,
            Events.DESCRIPTION to e.notes,
            Events.EVENT_LOCATION to e.location,
            Events.DTSTART to start,
            Events.DTEND to if (recurring) null else end,
            Events.DURATION to if (recurring) "P${(end - start).coerceAtLeast(0) / 1000}S" else null,
            Events.RRULE to e.rrule,
            Events.EXDATE to if (recurring && exdates.isNotEmpty()) {
                exdates.joinToString(",") { if (e.allDay) ICalTime.dateOf(it, zone).value else ICalTime.utc(it).value }
            } else {
                null
            },
            Events.ALL_DAY to if (e.allDay) 1 else 0,
            Events.EVENT_TIMEZONE to if (e.allDay) "UTC" else e.zone,
            // Klean's own reminders ring; the system calendar must not ring a second time.
            Events.HAS_ALARM to 0,
            Events.AVAILABILITY to Events.AVAILABILITY_BUSY,
        )
    }
}

/** The events the mirror reads: one by id, and every live one. */
class MirrorSource(val get: suspend (String) -> EventEntity?, val all: suspend () -> List<EventEntity>)

/**
 * Mirrors Klean's own events (Room is the source of truth) into one read-only "Klean" calendar under the Klean
 * Android account, so other calendar apps and the system show them. Writes as the account's sync adapter.
 * Runs after every event write and as a full resync at startup and after boot ([Reschedulable]). Needs
 * WRITE_CALENDAR; without it, it does nothing. With the module off, the calendar is removed.
 */
class KleanCalendarMirror(
    private val provider: CalendarProviderOps,
    private val source: MirrorSource,
    private val gate: ModuleGate,
    private val accountName: String,
    private val accountType: String,
    private val ensureAccount: suspend () -> Unit,
    private val zoneId: () -> String = { TimeZone.currentSystemDefault().id },
) : EventChangeListener, Reschedulable {
    private val lock = Mutex()

    override suspend fun onEventsChanged(ids: List<String>) {
        if (ids.isEmpty()) return
        lock.withLock {
            if (!provider.canWrite() || !gate.isEnabled(ModuleId.CALENDAR)) return
            val cal = ensureCalendar()
            val existing = provider.syncIds(cal)
            for (id in ids) {
                val e = source.get(id)
                val row = existing[id]
                when {
                    e == null || e.sync.deleted || e.calendarId != null -> row?.let(provider::deleteEvent)
                    row != null -> provider.updateEvent(row, MirrorValues.event(e, cal))
                    else -> provider.insertEvent(MirrorValues.event(e, cal))
                }
            }
        }
    }

    override suspend fun reschedule() = lock.withLock {
        if (!provider.canWrite()) return@withLock
        if (!gate.isEnabled(ModuleId.CALENDAR)) {
            provider.findKleanCalendar()?.let(provider::deleteCalendar)
            return@withLock
        }
        val cal = ensureCalendar()
        val existing = provider.syncIds(cal).toMutableMap()
        for (e in source.all()) {
            if (e.sync.deleted || e.calendarId != null) continue
            val row = existing.remove(e.id)
            if (row != null) provider.updateEvent(row, MirrorValues.event(e, cal)) else provider.insertEvent(MirrorValues.event(e, cal))
        }
        existing.values.forEach(provider::deleteEvent)
    }

    /** Removes the Klean calendar and its rows (the module was switched off). */
    suspend fun removeAll() = lock.withLock {
        if (provider.canWrite()) provider.findKleanCalendar()?.let(provider::deleteCalendar)
    }

    private suspend fun ensureCalendar(): Long = provider.findKleanCalendar() ?: run {
        ensureAccount()
        provider.createCalendar(MirrorValues.calendar(accountName, accountType, zoneId()))
    }
}

/** [CalendarProviderOps] over the real ContentResolver, as the Klean account's sync adapter. */
@Singleton
class AndroidCalendarProvider @Inject constructor(@ApplicationContext private val ctx: Context) : CalendarProviderOps {
    private val cr get() = ctx.contentResolver
    private val accountType get() = KleanAccount.accountType(ctx)

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, KleanAccount.NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
        .build()

    override fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun findKleanCalendar(): Long? = cr.query(
        Calendars.CONTENT_URI, arrayOf(Calendars._ID),
        "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.ACCOUNT_NAME} = ?", arrayOf(accountType, KleanAccount.NAME), null,
    )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    override fun createCalendar(values: Map<String, Any?>): Long =
        cr.insert(Calendars.CONTENT_URI.asSyncAdapter(), values.toContentValues())?.let(ContentUris::parseId)
            ?: error("calendar insert refused")

    override fun deleteCalendar(id: Long) {
        cr.delete(ContentUris.withAppendedId(Calendars.CONTENT_URI, id).asSyncAdapter(), null, null)
    }

    override fun syncIds(calendarId: Long): Map<String, Long> = cr.query(
        Events.CONTENT_URI.asSyncAdapter(), arrayOf(Events._ID, Events._SYNC_ID),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0", arrayOf(calendarId.toString()), null,
    )?.use { c ->
        buildMap { while (c.moveToNext()) c.getString(1)?.let { put(it, c.getLong(0)) } }
    }.orEmpty()

    override fun insertEvent(values: Map<String, Any?>): Long =
        cr.insert(Events.CONTENT_URI.asSyncAdapter(), values.toContentValues())?.let(ContentUris::parseId) ?: -1

    override fun updateEvent(id: Long, values: Map<String, Any?>) {
        cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(), values.toContentValues(), null, null)
    }

    override fun deleteEvent(id: Long) {
        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(), null, null)
    }

    private fun Map<String, Any?>.toContentValues() = ContentValues().apply {
        for ((k, v) in this@toContentValues) when (v) {
            null -> putNull(k)
            is String -> put(k, v)
            is Int -> put(k, v)
            is Long -> put(k, v)
            is Boolean -> put(k, if (v) 1 else 0)
            else -> put(k, v.toString())
        }
    }
}

/** Builds the mirror from the app's real pieces. */
@Singleton
class KleanCalendarMirrorFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val provider: AndroidCalendarProvider,
    private val repo: dagger.Lazy<CalendarRepository>,
    private val gate: ModuleGate,
) {
    val mirror: KleanCalendarMirror by lazy {
        KleanCalendarMirror(
            provider,
            MirrorSource({ repo.get().get(it) }, { repo.get().liveEvents() }),
            gate,
            accountName = KleanAccount.NAME,
            accountType = KleanAccount.accountType(ctx),
            ensureAccount = { KleanAccount.ensure(ctx) },
        )
    }

    suspend fun onIo(block: suspend KleanCalendarMirror.() -> Unit) = withContext(Dispatchers.IO) { mirror.block() }
}

/**
 * The mirror as the app wires it: an [EventChangeListener] after every write and a [Reschedulable] for the full
 * resync (startup, boot, permission granted, module switched on or off). Provider work runs on IO.
 */
@Singleton
class MirrorHook @Inject constructor(private val factory: KleanCalendarMirrorFactory) : EventChangeListener, Reschedulable {
    override suspend fun onEventsChanged(ids: List<String>) = factory.onIo { onEventsChanged(ids) }
    override suspend fun reschedule() = factory.onIo { reschedule() }
}
