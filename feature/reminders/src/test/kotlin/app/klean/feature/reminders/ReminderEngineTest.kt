package app.klean.feature.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.QuietHoursService
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.reminders.notify.ReminderNotifier
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.module.ModuleId
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.atMinute
import app.klean.core.data.db.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val nov9 = LocalDate(2026, 11, 9)
    private val noon = atMinute(nov9, 12 * 60, Dubai)
    private val clock = FakeClock(noon)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = FakeGate()
    private val quiet = QuietHoursService(settings, DubaiZone, clock)
    private val scheduler = ReminderScheduler(ctx, db, gate, quiet, clock, AlarmScheduler(ctx))
    private val repo = DefaultReminderRepository(db, stamper, clock, setOf<ReminderChangeListener>(scheduler))
    private val log = ReminderPromptLog(db, stamper, clock)

    private val pings = mutableListOf<Triple<String, PromptFormat, Boolean>>()
    private val cancelled = mutableListOf<String>()
    private val notifier = object : ReminderNotifier {
        override fun ping(r: ReminderEntity, format: PromptFormat, quiet: Boolean) { pings += Triple(r.id, format, quiet) }
        override fun alarmFallback(r: ReminderEntity) { pings += Triple(r.id, PromptFormat.HEADS_UP, false) }
        override fun cancel(id: String) { cancelled += id }
    }
    private val rang = mutableListOf<String>()
    private val stopped = mutableListOf<String>()
    private var ringWorks = true
    private val ringer = object : AlarmRinger {
        override fun ring(r: ReminderEntity): Boolean { rang += r.id; return ringWorks }
        override fun stop(id: String) { stopped += id }
    }
    private var refreshes = 0
    private val refresh = dagger.Lazy { StatusRefresh { setOf(object : app.klean.core.ui.module.StatusSurface { override suspend fun refresh() { refreshes++ } }) } }
    private val firer = ReminderFirer(db, repo, scheduler, gate, prefs, quiet, notifier, ringer, log, clock, refresh, app.klean.core.notifications.PromptAudience(db, prefs))
    private val actions = ReminderActions(db, repo, notifier, ringer, log, clock, DubaiZone, refresh)

    @Before fun exact() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @After fun close() {
        ShadowAlarmManager.reset()
        db.close()
    }

    private fun alarmFor(id: String) = shadowOf(am).scheduledAlarms.firstOrNull {
        shadowOf(it.operation).savedIntent.getStringExtra(ReminderScheduler.EXTRA_ID) == id
    }

    // Scheduling.
    @Test fun pingIsArmedAtItsTime() = runBlocking {
        val id = repo.add("stretch", noon + 2 * MINUTE_MS, "Asia/Dubai", isAlarm = false)
        assertEquals(noon + 2 * MINUTE_MS, alarmFor(id)!!.triggerAtMs)
        assertTrue(scheduler.isArmed(id))
    }

    @Test fun pingDuringQuietHoursWaitsForMorningButAlarmDoesnt() = runBlocking {
        val night = atMinute(LocalDate(2026, 11, 10), 3 * 60, Dubai)
        val ping = repo.add("ping", night, "Asia/Dubai", isAlarm = false)
        val alarm = repo.add("alarm", night, "Asia/Dubai", isAlarm = true)
        assertEquals(atMinute(LocalDate(2026, 11, 10), 8 * 60, Dubai), alarmFor(ping)!!.triggerAtMs)
        assertEquals(night, alarmFor(alarm)!!.triggerAtMs)
        // Alarms go through setAlarmClock (status-bar icon, exempt from Doze).
        assertNotNull(alarmFor(alarm)!!.alarmClockInfo)
        assertNull(alarmFor(ping)!!.alarmClockInfo)
    }

    @Test fun doneOrDeletedCancelsTheAlarm() = runBlocking {
        val a = repo.add("a", noon + HOUR_MS, "Asia/Dubai", isAlarm = false)
        val b = repo.add("b", noon + HOUR_MS, "Asia/Dubai", isAlarm = true)
        repo.markDone(a)
        repo.delete(b)
        assertFalse(scheduler.isArmed(a))
        assertFalse(scheduler.isArmed(b))
    }

    @Test fun moduleOffArmsNothing() = runBlocking {
        val id = repo.add("a", noon + HOUR_MS, "Asia/Dubai", isAlarm = false)
        gate.setEnabled(ModuleId.REMINDERS, false)
        scheduler.cancelAll()
        assertFalse(scheduler.isArmed(id))
        scheduler.reschedule()
        assertFalse(scheduler.isArmed(id))
        assertEquals(ReminderFirer.Result.SKIPPED, firer.fire(id))
        gate.setEnabled(ModuleId.REMINDERS, true)
        scheduler.reschedule()
        assertTrue(scheduler.isArmed(id))
    }

    // Firing.
    @Test fun pingFiresAndLogsForRotation() = runBlocking {
        val id = repo.add("stretch", noon, "Asia/Dubai", isAlarm = false)
        assertEquals(ReminderFirer.Result.PINGED, firer.fire(id))
        assertEquals(listOf(Triple(id, PromptFormat.STANDARD, false)), pings)
        val r = db.reminderDao().get(id)!!
        assertEquals(RINGING, r.state)
        assertEquals(noon, r.lastFiredAt)
        assertFalse(scheduler.isArmed(id))
        val row = db.promptLogDao().recentForTrigger(PromptTrigger.REMINDER, 5).single()
        assertEquals(id, row.triggerDetail)
        assertEquals(PromptOutcome.PENDING, row.outcome)
        assertEquals("reminders", row.moduleId)
        assertEquals(1, refreshes)
        // Diet never sees reminder prompts.
        assertTrue(db.promptLogDao().recent(5).isEmpty())
        assertNull(db.promptLogDao().lastSentAt())
    }

    @Test fun anIgnoredPingChangesTheNextFormat() = runBlocking {
        val a = repo.add("a", noon, "Asia/Dubai", isAlarm = false)
        firer.fire(a)
        clock.nowMs += HOUR_MS
        val b = repo.add("b", clock.nowMs, "Asia/Dubai", isAlarm = false)
        firer.fire(b)
        assertEquals(PromptFormat.HEADS_UP, pings.last().second)
        // Acting on one keeps its format next time.
        actions.done(b)
        clock.nowMs += HOUR_MS
        val c = repo.add("c", clock.nowMs, "Asia/Dubai", isAlarm = false)
        firer.fire(c)
        assertEquals(PromptFormat.HEADS_UP, pings.last().second)
    }

    @Test fun pingInsideQuietHoursIsRearmed() = runBlocking {
        val night = atMinute(LocalDate(2026, 11, 10), 2 * 60, Dubai)
        clock.nowMs = night
        val id = repo.add("late", night, "Asia/Dubai", isAlarm = false)
        assertEquals(ReminderFirer.Result.REARMED, firer.fire(id))
        assertTrue(pings.isEmpty())
        assertEquals(atMinute(LocalDate(2026, 11, 10), 8 * 60, Dubai), alarmFor(id)!!.triggerAtMs)
    }

    @Test fun earlyFireIsRearmed() = runBlocking {
        val id = repo.add("later", noon + HOUR_MS, "Asia/Dubai", isAlarm = false)
        assertEquals(ReminderFirer.Result.REARMED, firer.fire(id))
        assertEquals(noon + HOUR_MS, alarmFor(id)!!.triggerAtMs)
    }

    @Test fun alarmRingsOnTheMainPhoneEvenInQuietHours() = runBlocking {
        val night = atMinute(LocalDate(2026, 11, 10), 2 * 60, Dubai)
        clock.nowMs = night
        val id = repo.add("wake", night, "Asia/Dubai", isAlarm = true)
        assertEquals(ReminderFirer.Result.RANG, firer.fire(id))
        assertEquals(listOf(id), rang)
        assertTrue(pings.isEmpty())
    }

    @Test fun alarmFallsBackToALoopingNotification() = runBlocking {
        ringWorks = false
        val id = repo.add("wake", noon, "Asia/Dubai", isAlarm = true)
        firer.fire(id)
        assertEquals(listOf(Triple(id, PromptFormat.HEADS_UP, false)), pings)
    }

    @Test fun alarmOnAnotherPhoneIsAQuietNote() = runBlocking {
        prefs.setIsMainPhone(false)
        val id = repo.add("wake", noon, "Asia/Dubai", isAlarm = true)
        assertEquals(ReminderFirer.Result.ALARM_ELSEWHERE, firer.fire(id))
        assertTrue(rang.isEmpty())
        assertEquals(listOf(Triple(id, PromptFormat.STANDARD, true)), pings)
    }

    @Test fun handledRemindersDontFire() = runBlocking {
        val id = repo.add("x", noon, "Asia/Dubai", isAlarm = false)
        repo.markDone(id)
        assertEquals(ReminderFirer.Result.SKIPPED, firer.fire(id))
    }

    // Actions.
    @Test fun snoozeTenMinutesRearms() = runBlocking {
        val id = repo.add("x", noon, "Asia/Dubai", isAlarm = true)
        firer.fire(id)
        actions.snooze(id, Snooze.TEN_MINUTES)
        val r = db.reminderDao().get(id)!!
        assertEquals("SNOOZED", r.state)
        assertEquals(noon + 10 * MINUTE_MS, r.snoozedUntil)
        assertEquals(noon + 10 * MINUTE_MS, alarmFor(id)!!.triggerAtMs)
        assertEquals(listOf(id), stopped)
        assertEquals(listOf(id), cancelled)
    }

    @Test fun pickedTimeSnoozes() = runBlocking {
        val id = repo.add("x", noon, "Asia/Dubai", isAlarm = false)
        firer.fire(id)
        actions.snoozeTo(id, 15 * 60)
        assertEquals(atMinute(nov9, 15 * 60, Dubai), db.reminderDao().get(id)!!.snoozedUntil)
    }

    @Test fun doneOnARecurringReminderMovesItToTheNextDay() = runBlocking {
        val id = repo.add("pills", noon, "Asia/Dubai", isAlarm = false, recurrence = "FREQ=DAILY")
        firer.fire(id)
        actions.done(id)
        val r = db.reminderDao().get(id)!!
        assertEquals("SCHEDULED", r.state)
        assertEquals(noon + 24 * HOUR_MS, r.fireAt)
        assertTrue(scheduler.isArmed(id))
        assertEquals(PromptOutcome.LOGGED, db.promptLogDao().recentForTrigger(PromptTrigger.REMINDER, 1).single().outcome)
    }

    @Test fun doneCompletesTheTaskButDismissDoesNot() = runBlocking {
        fun task(id: String) = TaskEntity(id = id, listId = "p", title = id, sync = runBlocking { stamper.stamp() })
        db.taskDao().upsert(task("t1"))
        db.taskDao().upsert(task("t2"))
        val a = repo.add("a", noon, "Asia/Dubai", isAlarm = true, taskId = "t1")
        val b = repo.add("b", noon, "Asia/Dubai", isAlarm = true, taskId = "t2")
        firer.fire(a)
        firer.fire(b)
        actions.done(a)
        actions.dismiss(b)
        assertNotNull(db.taskDao().get("t1")!!.completedAt)
        assertNull(db.taskDao().get("t2")!!.completedAt)
        assertEquals("DISMISSED", db.reminderDao().get(b)!!.state)
    }

    @Test fun swipeIsRecordedAsDismissed() = runBlocking {
        val id = repo.add("x", noon, "Asia/Dubai", isAlarm = false)
        firer.fire(id)
        actions.swiped(id)
        assertEquals(PromptOutcome.DISMISSED, db.promptLogDao().recentForTrigger(PromptTrigger.REMINDER, 1).single().outcome)
        assertEquals(RINGING, db.reminderDao().get(id)!!.state)
    }
}
