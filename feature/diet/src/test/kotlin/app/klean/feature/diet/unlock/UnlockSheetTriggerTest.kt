package app.klean.feature.diet.unlock

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.module.ModuleId
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class FakeUnlockPoster : UnlockSheetPoster {
    val posted = mutableListOf<String>()
    override suspend fun post(logId: String) { posted += logId }
}

/** Plan M11 Task 11.2: when the screen-state events lead to the unlock sheet. Nov 9 2026, Asia/Dubai. */
@RunWith(AndroidJUnit4::class)
class UnlockSheetTriggerTest {
    private val clock = FakeClock(t(10))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val poster = FakeUnlockPoster()
    private val trigger = UnlockSheetTrigger(db, PromptLogWriter(db, stamper, clock), settings, gate, prefs, clock, DubaiZone, poster)

    @After fun tearDown() = db.close()

    private fun t(h: Int, m: Int = 0) = EngineRig.t(h, m)

    /** Screen off at [offAt], back on (and unlocked) at [onAt]. */
    private suspend fun lockedBetween(offAt: Long, onAt: Long): Boolean {
        clock.nowMs = offAt
        trigger.onScreenOff()
        clock.nowMs = onAt
        return trigger.onWake()
    }

    @Test fun nothingWithoutAKnownScreenOff() = runBlocking {
        clock.nowMs = t(14)
        assertFalse(trigger.onWake())
        assertTrue(poster.posted.isEmpty())
    }

    @Test fun showsAfterThreeHoursOffAndRecordsAPromptRow() = runBlocking {
        assertTrue(lockedBetween(t(10), t(13)))
        val row = db.promptLogDao().recent(10).single()
        assertEquals(listOf(row.id), poster.posted)
        assertEquals(PromptTrigger.UNLOCK_SHEET, row.trigger)
        assertEquals(PromptFormat.HEADS_UP, row.format)
        assertEquals(PromptOutcome.PENDING, row.outcome)
        assertEquals(ModuleId.DIET.value, row.moduleId)
        assertNull(row.triggerDetail)
        assertEquals(t(13), row.sentAt)
    }

    @Test fun notAfterAShortBreak() = runBlocking {
        assertFalse(lockedBetween(t(10), t(12, 59)))
        assertTrue(poster.posted.isEmpty())
        assertTrue(db.promptLogDao().recent(10).isEmpty())
    }

    /** Screen on while locked, then the unlock itself: one sheet, not two. */
    @Test fun screenOnThenUnlockShowsOnce() = runBlocking {
        assertTrue(lockedBetween(t(10), t(13)))
        clock.nowMs = t(13) + MINUTE_MS
        assertFalse(trigger.onWake())
        assertEquals(1, poster.posted.size)
    }

    @Test fun showsAgainAfterTheNextLongBreakOnly() = runBlocking {
        assertTrue(lockedBetween(t(10), t(13)))
        assertFalse(lockedBetween(t(13, 30), t(16, 29)))
        assertTrue(lockedBetween(t(16, 30), t(19, 30)))
        assertEquals(2, poster.posted.size)
    }

    @Test fun notInQuietHours() = runBlocking {
        // Off 21:00 → on 03:00 next day (default quiet hours 00:00–08:00).
        clock.nowMs = t(21)
        trigger.onScreenOff()
        clock.nowMs = t(21) + 6 * HOUR_MS
        assertFalse(trigger.onWake())
        assertTrue(poster.posted.isEmpty())
    }

    @Test fun notWhenTheSettingIsOff() = runBlocking {
        settings.set(SyncedKeys.DietUnlockSheet, false)
        assertFalse(lockedBetween(t(10), t(14)))
        assertTrue(poster.posted.isEmpty())
    }

    @Test fun notWhenDietIsOff() = runBlocking {
        gate.setEnabled(ModuleId.DIET, false)
        assertFalse(lockedBetween(t(10), t(14)))
        assertTrue(poster.posted.isEmpty())
    }

    @Test fun processStartWithTheScreenOffCountsFromNow() = runBlocking {
        clock.nowMs = t(9)
        trigger.onScreenOff()
        clock.nowMs = t(11)
        trigger.onProcessStart(interactive = false)
        assertEquals(t(11), prefs.lastScreenOffAt.first())
        clock.nowMs = t(13)
        assertFalse(trigger.onWake())
    }

    @Test fun processStartWithTheScreenOnLeavesTheLastScreenOffAlone() = runBlocking {
        clock.nowMs = t(9)
        trigger.onScreenOff()
        clock.nowMs = t(11)
        trigger.onProcessStart(interactive = true)
        assertEquals(t(9), prefs.lastScreenOffAt.first())
    }

    /** Debug "Pretend the phone was locked for 3 h": the next wake shows the sheet, once. */
    @Test fun pretendLongBreakShowsOnTheNextWakeOnce() = runBlocking {
        assertTrue(lockedBetween(t(10), t(13)))
        trigger.pretendLongBreak()
        assertTrue(lockedBetween(t(13, 5), t(13, 6)))
        assertFalse(prefs.unlockSheetPretend.first())
        assertFalse(lockedBetween(t(13, 7), t(13, 8)))
        assertEquals(2, poster.posted.size)
    }
}
