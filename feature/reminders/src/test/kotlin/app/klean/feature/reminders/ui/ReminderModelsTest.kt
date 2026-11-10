package app.klean.feature.reminders.ui

import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.feature.reminders.Dubai
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderModelsTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val now = atMinute(nov9, 10 * 60, Dubai)
    private fun r(id: String, at: Long, state: String = "SCHEDULED", snoozed: Long? = null, deleted: Boolean = false) =
        ReminderEntity(id = id, title = id, fireAt = at, zone = "Asia/Dubai", state = state, snoozedUntil = snoozed, sync = SyncColumns(0, 0, "d", deleted, 1))

    @Test fun groupsByDay() {
        val g = ReminderGroups.build(
            listOf(
                r("fired", now - HOUR_MS, "RINGING"), r("done", now - HOUR_MS, "DONE"), r("gone", now, deleted = true),
                r("later today", now + 5 * HOUR_MS), r("soon", now + HOUR_MS),
                r("snoozed", now - 2 * HOUR_MS, "SNOOZED", snoozed = now + 10 * MINUTE_MS),
                r("tomorrow", now + 24 * HOUR_MS), r("next week", now + 7 * 24 * HOUR_MS),
            ),
            now, Dubai,
        )
        assertEquals(listOf("fired"), g.earlier.map { it.id })
        assertEquals(listOf("snoozed", "soon", "later today"), g.today.map { it.id })
        assertTrue(g.today.first().snoozed)
        assertEquals(listOf("tomorrow"), g.tomorrow.map { it.id })
        assertEquals(listOf("next week"), g.later.map { it.id })
    }

    @Test fun textWithTime() {
        val d = ReminderText.parse("remind me at 5 to call Ahmed", now, Dubai)
        assertEquals("call Ahmed", d.title)
        assertEquals(atMinute(nov9, 17 * 60, Dubai), d.fireAt)
        assertFalse(d.isAlarm)
    }

    @Test fun inTwoMinutes() {
        val d = ReminderText.parse("remind me in 2 min to stretch", now, Dubai)
        assertEquals("stretch", d.title)
        assertEquals(now + 2 * MINUTE_MS, d.fireAt)
    }

    @Test fun noTimeMeansTomorrowAtNine() {
        val d = ReminderText.parse("remind me to water the plants", now, Dubai)
        assertEquals("water the plants", d.title)
        assertEquals(atMinute(LocalDate(2026, 11, 10), 9 * 60, Dubai), d.fireAt)
    }

    @Test fun alarmWords() {
        val d = ReminderText.parse("alarm tomorrow 6:30am gym", now, Dubai)
        assertTrue(d.isAlarm)
        assertEquals("gym", d.title)
        assertEquals(atMinute(LocalDate(2026, 11, 10), 6 * 60 + 30, Dubai), d.fireAt)
        assertTrue(ReminderText.parse("wake me up at 7", now, Dubai).isAlarm)
    }
}
