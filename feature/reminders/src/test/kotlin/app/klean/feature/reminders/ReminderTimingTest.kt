package app.klean.feature.reminders

import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimingTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val now = atMinute(nov9, 12 * 60, Dubai)
    private fun r(
        fireAt: Long, state: String = "SCHEDULED", snoozed: Long? = null, alarm: Boolean = false,
        recurrence: String? = null, zone: String = "Asia/Dubai",
    ) = ReminderEntity(
        id = "r", title = "t", fireAt = fireAt, zone = zone, isAlarm = alarm, recurrence = recurrence,
        state = state, snoozedUntil = snoozed, sync = SyncColumns(0, 0, "d", false, 1),
    )

    @Test fun dueIsTheSnoozeTimeWhenSnoozed() {
        assertEquals(now + HOUR_MS, ReminderTiming.dueAt(r(now, "SNOOZED", snoozed = now + HOUR_MS)))
        assertEquals(now, ReminderTiming.dueAt(r(now)))
    }

    @Test fun pingsWaitForQuietHoursToEndAlarmsDont() {
        val night = atMinute(LocalDate(2026, 11, 10), 2 * 60, Dubai)
        val morning = atMinute(LocalDate(2026, 11, 10), 8 * 60, Dubai)
        val nextAllowed = { at: Long -> if (at in night - 3 * HOUR_MS until morning) morning else at }
        assertEquals(morning, ReminderTiming.armAt(r(night), now, nextAllowed))
        assertEquals(night, ReminderTiming.armAt(r(night, alarm = true), now, nextAllowed))
    }

    @Test fun pastDueArmsRightAway() {
        assertEquals(now, ReminderTiming.armAt(r(now - HOUR_MS), now) { it })
    }

    @Test fun snoozeOptions() {
        val tz = Dubai
        val fired = atMinute(nov9, 7 * 60, tz)
        assertEquals(now + 10 * MINUTE_MS, ReminderTiming.snoozeUntil(Snooze.TEN_MINUTES, now, r(fired), tz))
        assertEquals(now + HOUR_MS, ReminderTiming.snoozeUntil(Snooze.ONE_HOUR, now, r(fired), tz))
        // Tomorrow at the reminder's own time of day.
        assertEquals(atMinute(LocalDate(2026, 11, 10), 7 * 60, tz), ReminderTiming.snoozeUntil(Snooze.TOMORROW, now, r(fired), tz))
    }

    @Test fun pickedTimeIsTodayOrTomorrow() {
        assertEquals(atMinute(nov9, 15 * 60, Dubai), ReminderTiming.pickedTime(15 * 60, now, Dubai))
        assertEquals(atMinute(LocalDate(2026, 11, 10), 9 * 60, Dubai), ReminderTiming.pickedTime(9 * 60, now, Dubai))
    }

    @Test fun nextOccurrenceKeepsLocalTimeInTheReminderZone() {
        val fire = atMinute(nov9, 17 * 60, Dubai)
        val next = ReminderTiming.nextOccurrence(r(fire, recurrence = "FREQ=DAILY"), now)!!
        assertEquals(atMinute(LocalDate(2026, 11, 10), 17 * 60, Dubai), next.fireAt)
        assertEquals("FREQ=DAILY", next.recurrence)
        // Set in London: 9:00 London time, whatever this phone's zone.
        val london = TimeZone.of("Europe/London")
        val l = ReminderTiming.nextOccurrence(r(atMinute(nov9, 9 * 60, london), recurrence = "FREQ=WEEKLY", zone = "Europe/London"), now)!!
        assertEquals(atMinute(LocalDate(2026, 11, 16), 9 * 60, london), l.fireAt)
    }

    @Test fun nextOccurrenceSkipsWhatAlreadyPassedAndCountsDown() {
        val longAgo = atMinute(LocalDate(2026, 11, 1), 9 * 60, Dubai)
        val n = ReminderTiming.nextOccurrence(r(longAgo, recurrence = "FREQ=DAILY;COUNT=20"), now)!!
        assertEquals(atMinute(LocalDate(2026, 11, 10), 9 * 60, Dubai), n.fireAt)
        assertEquals("FREQ=DAILY;COUNT=11", n.recurrence)
        assertNull(ReminderTiming.nextOccurrence(r(longAgo, recurrence = "FREQ=DAILY;COUNT=3"), now))
        assertNull(ReminderTiming.nextOccurrence(r(now), now))
        assertNull(ReminderTiming.nextOccurrence(r(now, recurrence = "garbage"), now))
    }
}
