package app.klean.feature.calendar

import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.EventEntity
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.DeviceCalendar
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.calendar.data.Exdates
import app.klean.feature.calendar.data.ImportResult
import app.klean.feature.calendar.data.SourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [CalendarRepository] for view-model and provider tests. */
class FakeCalendarRepository : CalendarRepository {
    val events = MutableStateFlow(emptyList<EventEntity>())
    val sources = MutableStateFlow(emptyList<CalendarSourceEntity>())
    val kleanSourceId = "klean-source"
    var imported: String? = null
    var exportText = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"
    private var n = 0

    override fun observeEvents(): Flow<List<EventEntity>> = events.map { l -> l.filter { !it.sync.deleted } }
    override suspend fun liveEvents(): List<EventEntity> = events.value.filter { !it.sync.deleted }
    override suspend fun get(id: String): EventEntity? = events.value.firstOrNull { it.id == id }

    override suspend fun add(draft: EventDraft): String {
        val id = "e${++n}"
        events.value = events.value + draft.toEntity(id)
        return id
    }

    override suspend fun update(id: String, draft: EventDraft) {
        events.value = events.value.map { if (it.id == id) draft.toEntity(id).copy(exdates = it.exdates) else it }
    }

    override suspend fun delete(id: String) {
        events.value = events.value.map { if (it.id == id) it.copy(sync = it.sync.copy(deleted = true)) else it }
    }

    override suspend fun skipOccurrence(id: String, occurrenceStart: Long) {
        events.value = events.value.map {
            if (it.id == id) it.copy(exdates = Exdates.encode(Exdates.decode(it.exdates) + occurrenceStart)) else it
        }
    }

    override suspend fun importIcs(text: String): ImportResult {
        imported = text
        return ImportResult(added = 2, updated = 1)
    }

    override suspend fun exportIcs(): String = exportText
    override suspend fun exportCount(): Int = liveEvents().size

    override fun observeSources(): Flow<List<CalendarSourceEntity>> = sources
    override suspend fun sources(): List<CalendarSourceEntity> = sources.value
    override suspend fun setSourceSelected(id: String, selected: Boolean) {
        sources.value = sources.value.map { if (it.id == id) it.copy(selected = selected) else it }
    }

    override suspend fun syncDeviceSources(calendars: List<DeviceCalendar>) {
        val kept = sources.value.filter { it.kind != SourceKind.DEVICE }
        sources.value = kept + calendars.map {
            val prev = sources.value.firstOrNull { s -> s.id == "device-${it.id}" }
            CalendarSourceEntity("device-${it.id}", SourceKind.DEVICE, it.name, it.color, it.id.toString(), true, prev?.selected ?: true, sync())
        }
    }

    override suspend fun deviceSourceId(calendarId: Long) = "device-$calendarId"

    override suspend fun ensureKleanSource(): CalendarSourceEntity =
        sources.value.firstOrNull { it.id == kleanSourceId }
            ?: CalendarSourceEntity(kleanSourceId, SourceKind.KLEAN, "Klean", sync = sync()).also { sources.value = sources.value + it }

    private fun EventDraft.toEntity(id: String) = EventEntity(
        id = id, title = title.trim(), startAt = startAt, endAt = endAt, allDay = allDay, zone = zone,
        location = location, notes = notes, rrule = rrule, reminderMinutes = reminderMinutes, alarm = alarm, uid = id,
        sync = sync(),
    )
}
