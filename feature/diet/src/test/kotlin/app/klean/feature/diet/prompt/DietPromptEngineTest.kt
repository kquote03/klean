package app.klean.feature.diet.prompt

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.QuietHoursService
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.prompt.MealLabel
import app.klean.shared.diet.prompt.PlannedPrompt
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class FakeScheduler : PromptAlarmScheduling {
    val scheduled = mutableListOf<PlannedPrompt>()
    var cancels = 0
    val last get() = scheduled.last()
    override fun schedule(plan: PlannedPrompt) { scheduled += plan }
    override fun cancel() { cancels++ }
}

class FakePresenter : PromptPresenter {
    data class Shown(val logId: String, val format: PromptFormat, val phrasingId: String, val trigger: PromptTrigger, val placeLabel: String? = null)
    val shown = mutableListOf<Shown>()
    val events = mutableListOf<String>()
    var cancels = 0
    override suspend fun present(logId: String, format: PromptFormat, phrasingId: String, trigger: PromptTrigger, placeLabel: String?) {
        shown += Shown(logId, format, phrasingId, trigger, placeLabel)
        events += "present"
    }
    override suspend fun cancelActive() { cancels++; events += "cancel" }
}

/** Everything real except the alarm and the notification: in-memory Room, real settings, gate and quiet hours. */
class EngineRig {
    val clock = FakeClock(t(10, 0))
    val db = inMemoryDb()
    val prefs = tempDevicePrefs(clock)
    val stamper = SyncStamper(db, prefs, clock)
    val settings = SettingsRepository(db, stamper)
    val gate = SettingsModuleGate(settings)
    val scheduler = FakeScheduler()
    val presenter = FakePresenter()
    val writer = PromptLogWriter(db, stamper, clock)
    val engine = DietPromptEngine(
        db, writer, settings, gate, QuietHoursService(settings, DubaiZone, clock), clock, DubaiZone,
        scheduler, presenter, prefs, Random(7),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val noAi = object : EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }
    /** Writes fan out to the engine, as in the app. */
    val repo = DefaultDietRepository(db, stamper, clock, DubaiZone, settings, DietChangeDispatcher(setOf(engine)), noAi, scope)

    suspend fun rows() = db.promptLogDao().recent(50)

    /** Advances the clock to the scheduled time and fires that alarm. */
    suspend fun fireNext() {
        val plan = scheduler.last
        clock.nowMs = plan.at
        engine.onAlarm(plan)
    }

    fun close() { scope.cancel(); db.close() }

    companion object {
        fun t(h: Int, m: Int = 0, day: Int = 9) = atMinute(LocalDate(2026, 11, day), h * 60 + m, Dubai)
    }
}

/** Plan M5 Task 5.5: the eight engine scenarios, at Nov 9 2026 10:00 Asia/Dubai, default quiet hours 00:00–08:00. */
@RunWith(AndroidJUnit4::class)
class DietPromptEngineTest {
    private val rigs = mutableListOf<EngineRig>()
    private fun rig() = EngineRig().also { rigs += it }
    private fun t(h: Int, m: Int = 0, day: Int = 9) = EngineRig.t(h, m, day)

    @After fun tearDown() = rigs.forEach { it.close() }

    @Test fun s1_noDataSchedulesSixHoursOut() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        assertEquals(PlannedPrompt(t(16), PromptTrigger.CADENCE, null), r.scheduler.last)
        assertEquals(t(16), r.prefs.nextPromptAt.first())
    }

    @Test fun s2_alarmPresentsStandardAndSchedulesNext() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.fireNext()
        val shown = r.presenter.shown.single()
        assertEquals(PromptFormat.STANDARD, shown.format)
        val row = r.rows().single()
        assertEquals(shown.logId, row.id)
        assertEquals(PromptOutcome.PENDING, row.outcome)
        assertEquals(t(16), row.sentAt)
        assertEquals(t(16), row.scheduledFor)
        assertEquals(t(22), r.scheduler.last.at)
        assertEquals(t(22), r.prefs.nextPromptAt.first())
    }

    @Test fun s3_unansweredPromptIsIgnoredAndTheNextChangesFormatNotFrequency() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.fireNext()
        r.fireNext()
        val (older, newer) = r.rows().sortedBy { it.sentAt }
        assertEquals(PromptOutcome.IGNORED, older.outcome)
        assertEquals(PromptOutcome.PENDING, newer.outcome)
        val (first, second) = r.presenter.shown
        assertEquals(PromptFormat.HEADS_UP, second.format)
        assertNotEquals(first.phrasingId, second.phrasingId)
        // 22:00 + 6 h = 04:00 is quiet → wake (08:00) + 1 h.
        assertEquals(t(9, day = 10), r.scheduler.last.at)
    }

    @Test fun s4_scheduleIsTheSameWhateverTheOutcome() = runBlocking {
        suspend fun run(outcome: PromptOutcome?): List<Long> {
            val r = rig()
            r.engine.reschedule()
            val times = mutableListOf(r.scheduler.last.at)
            r.fireNext()
            times += r.scheduler.last.at
            if (outcome != null) {
                r.clock.nowMs = t(16, 30)
                // Set by hand: DISMISSED as from a swipe, LOGGED with no entry behind it.
                r.writer.setOutcome(r.rows().single().id, outcome)
            }
            r.fireNext()
            times += r.scheduler.last.at
            r.fireNext()
            times += r.scheduler.last.at
            return times
        }
        val ignored = run(null)
        assertEquals(listOf(t(16), t(22), t(9, day = 10), t(15, day = 10)), ignored)
        assertEquals(ignored, run(PromptOutcome.DISMISSED))
        assertEquals(ignored, run(PromptOutcome.LOGGED))
    }

    @Test fun s5_quietHoursPresentNothing() = runBlocking {
        val r = rig()
        r.clock.nowMs = t(2, day = 10)
        r.engine.onAlarm(PlannedPrompt(r.clock.nowMs, PromptTrigger.CADENCE, null))
        assertTrue(r.presenter.shown.isEmpty())
        assertTrue(r.rows().isEmpty())
        // It still re-arms, outside quiet hours.
        assertTrue(r.scheduler.scheduled.isNotEmpty())
        assertTrue(r.scheduler.last.at >= t(8, day = 10))
    }

    @Test fun s6_aLogAfterAPromptMarksItLoggedKeepsTheFormatAndMovesTheNext() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.fireNext()
        r.clock.nowMs = t(16, 20)
        r.repo.logEntry("dates and coffee", CaptureSource.PROMPT_REPLY)
        assertEquals(PromptOutcome.LOGGED, r.rows().single().outcome)
        assertTrue(r.presenter.cancels >= 1)
        assertEquals(t(22, 20), r.scheduler.last.at)
        r.fireNext()
        assertEquals(PromptFormat.STANDARD, r.presenter.shown.last().format)
    }

    @Test fun s7_learnedLunchSlotIsUsed() = runBlocking {
        val r = rig()
        for (day in 2..8) {
            for ((h, m) in listOf(8 to 10, 13 to 20, 20 to 5)) {
                r.repo.logEntry("meal", CaptureSource.MODULE_SCREEN, eatenAt = t(h, m, day))
            }
        }
        r.repo.logEntry("breakfast", CaptureSource.MODULE_SCREEN, eatenAt = t(8, 10))
        r.engine.reschedule()
        // Lunch bin 13:00–13:30 → centre 13:15, prompt 45 min later.
        assertEquals(PlannedPrompt(t(14, 0), PromptTrigger.LEARNED_MEAL, MealLabel.LUNCH), r.scheduler.last)
        r.fireNext()
        assertTrue(r.presenter.shown.single().phrasingId in setOf("m_l1", "m_l2"))
    }

    @Test fun s7b_learningOffFallsBackToCadence() = runBlocking {
        val r = rig()
        r.settings.set(SyncedKeys.DietLearnMealTimes, false)
        for (day in 2..8) r.repo.logEntry("lunch", CaptureSource.MODULE_SCREEN, eatenAt = t(13, 20, day))
        r.repo.logEntry("breakfast", CaptureSource.MODULE_SCREEN, eatenAt = t(8, 10))
        r.engine.reschedule()
        assertEquals(PlannedPrompt(t(14, 10), PromptTrigger.CADENCE, null), r.scheduler.last)
    }

    @Test fun s8_dietDisabledCancelsAndSchedulesNothing() = runBlocking {
        val r = rig()
        r.gate.setEnabled(ModuleId.DIET, false)
        r.engine.reschedule()
        assertTrue(r.scheduler.scheduled.isEmpty())
        assertEquals(1, r.scheduler.cancels)
        assertEquals(1, r.presenter.cancels)
        assertNull(r.prefs.nextPromptAt.first())
        r.engine.onAlarm(PlannedPrompt(t(16), PromptTrigger.CADENCE, null))
        assertTrue(r.presenter.shown.isEmpty())
    }

    @Test fun s8b_promptsSwitchedOffCancelsToo() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.settings.set(SyncedKeys.DietPromptsEnabled, false)
        r.engine.reschedule()
        assertEquals(1, r.scheduler.scheduled.size)
        assertEquals(1, r.scheduler.cancels)
    }

    @Test fun recentEntrySkipsThePromptButReArms() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.clock.nowMs = t(15, 0)
        r.repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        r.clock.nowMs = t(16, 0)
        r.engine.onAlarm(PlannedPrompt(t(16), PromptTrigger.CADENCE, null))
        assertTrue(r.presenter.shown.isEmpty())
        assertEquals(t(21), r.scheduler.last.at)
    }

    @Test fun forcedAlarmIgnoresQuietHoursAndRecentEntries() = runBlocking {
        val r = rig()
        r.repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        r.engine.onAlarm(PlannedPrompt(r.clock.nowMs, PromptTrigger.CADENCE, null), force = true)
        assertEquals(1, r.presenter.shown.size)
    }

    @Test fun aNewCheckInClearsThePreviousOneFirst() = runBlocking {
        val r = rig()
        r.engine.reschedule()
        r.fireNext()
        r.fireNext()
        // Formats differ (a highlight never replaces a notification by id), so the old one is cleared explicitly.
        assertEquals(listOf("cancel", "present", "cancel", "present"), r.presenter.events)
    }
}
