package app.klean.feature.calendar.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.feature.calendar.data.EventChangeListener
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.FakeModuleGate
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.data.DefaultCalendarRepository
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.inMemoryDb
import app.klean.feature.calendar.tempDevicePrefs
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventReminderSyncTest {
    private val now = dubai(2026, 11, 10, 12)
    private val clock = FakeClock(now)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val gate = FakeModuleGate()
    private lateinit var sync: EventReminderSync
    private val reminders = DefaultReminderRepository(db, stamper, clock, setOf(ReminderChangeListener { sync.onChanged(it) }))
    private val repo = DefaultCalendarRepository(db, stamper, clock, DubaiZone, prefs, setOf(EventChangeListener { sync.onEventsChanged(it) }))

    init {
        sync = EventReminderSync({ reminders }, { repo }, db, clock, gate)
    }

    private val start = dubai(2026, 11, 13, 9, 30)
    private val draft = EventDraft("Dentist", start, start + HOUR, zone = "Asia/Dubai", reminderMinutes = 10)

    @After fun close() = db.close()

    private suspend fun reminderFor(eventId: String) = db.reminderDao().get(EventReminderSync.reminderId(eventId))

    @Test fun anEventWithAReminderGetsOneQuietReminder() = runBlocking {
        val id = repo.add(draft)
        val r = reminderFor(id)!!
        assertEquals("Dentist", r.title)
        assertEquals(start - 10 * 60_000L, r.fireAt)
        assertFalse(r.isAlarm)
        assertEquals("SCHEDULED", r.state)
        assertFalse(r.sync.deleted)
    }

    @Test fun movingTheEventMovesTheReminderAndRemovingItDeletesIt() = runBlocking {
        val id = repo.add(draft)
        repo.update(id, draft.copy(startAt = start + DAY, endAt = start + DAY + HOUR, reminderMinutes = 60))
        assertEquals(start + DAY - HOUR, reminderFor(id)!!.fireAt)
        repo.update(id, draft.copy(reminderMinutes = null))
        assertTrue(reminderFor(id)!!.sync.deleted)
    }

    @Test fun deletingTheEventDeletesTheReminder() = runBlocking {
        val id = repo.add(draft)
        repo.delete(id)
        assertTrue(reminderFor(id)!!.sync.deleted)
    }

    @Test fun noReminderMeansNoRow() = runBlocking {
        val id = repo.add(draft.copy(reminderMinutes = null))
        assertNull(reminderFor(id))
    }

    @Test fun aRecurringEventsReminderMovesOnOnceDone() = runBlocking {
        val id = repo.add(draft.copy(rrule = "FREQ=WEEKLY"))
        val rid = EventReminderSync.reminderId(id)
        clock.nowMs = start
        reminders.markDone(rid)
        val r = reminderFor(id)!!
        assertEquals(start + 7 * DAY - 10 * 60_000L, r.fireAt)
        assertEquals("SCHEDULED", r.state)
    }

    @Test fun aDoneOneOffReminderIsLeftAlone() = runBlocking {
        val id = repo.add(draft)
        clock.nowMs = start
        reminders.markDone(EventReminderSync.reminderId(id))
        sync.reschedule()
        val r = reminderFor(id)!!
        assertEquals("DONE", r.state)
        assertFalse(r.sync.deleted)
    }

    @Test fun turningCalendarOffRemovesItsRemindersAndOnBringsThemBack() = runBlocking {
        val id = repo.add(draft)
        gate.setEnabled(ModuleId.CALENDAR, false)
        sync.reschedule()
        assertTrue(reminderFor(id)!!.sync.deleted)
        gate.setEnabled(ModuleId.CALENDAR, true)
        sync.reschedule()
        assertFalse(reminderFor(id)!!.sync.deleted)
        assertEquals("SCHEDULED", reminderFor(id)!!.state)
    }

    @Test fun unchangedEventsDoNotRewriteTheReminder() = runBlocking {
        val id = repo.add(draft)
        val seq = reminderFor(id)!!.sync.changeSeq
        sync.reschedule()
        assertEquals(seq, reminderFor(id)!!.sync.changeSeq)
    }
}
