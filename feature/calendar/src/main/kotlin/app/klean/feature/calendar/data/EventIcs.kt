package app.klean.feature.calendar.data

import app.klean.core.data.db.entity.EventEntity
import app.klean.shared.ical.ICalEvent
import app.klean.shared.ical.ICalTime
import kotlinx.datetime.TimeZone

/** Between Klean's `event` rows and the shared iCalendar codec (ICS import/export). */
object EventIcs {
    private fun zoneOf(e: EventEntity): TimeZone = runCatching { TimeZone.of(e.zone) }.getOrDefault(TimeZone.UTC)

    fun toICal(e: EventEntity): ICalEvent {
        val tz = zoneOf(e)
        val time: (Long) -> ICalTime = if (e.allDay) { ms -> ICalTime.dateOf(ms, tz) } else ICalTime::utc
        return ICalEvent(
            uid = e.uid ?: e.id,
            summary = e.title,
            description = e.notes,
            location = e.location,
            start = time(e.startAt),
            end = time(e.endAt),
            rrule = e.rrule,
            exdates = Exdates.decode(e.exdates).map(time),
            alarmMinutesBefore = e.reminderMinutes,
            dtstamp = ICalTime.utc(e.sync.updatedAt).value,
        )
    }

    /** [fallback] reads floating times and dates that carry no TZID. */
    fun toDraft(ical: ICalEvent, fallback: TimeZone): EventDraft {
        val zone = ical.start.tzid?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() } ?: fallback
        val start = ical.start.toEpochMs(zone)
        return EventDraft(
            title = ical.summary.trim(),
            startAt = start,
            endAt = maxOf(start, ical.endEpochMs(zone)),
            allDay = ical.allDay,
            zone = zone.id,
            location = ical.location,
            notes = ical.description,
            rrule = ical.rrule,
            reminderMinutes = ical.alarmMinutesBefore?.takeIf { it >= 0 },
            uid = ical.uid,
        )
    }

    fun exdates(ical: ICalEvent, fallback: TimeZone): List<Long> {
        val zone = ical.start.tzid?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() } ?: fallback
        return ical.exdates.map { it.toEpochMs(zone) }
    }
}
