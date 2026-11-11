package app.klean.feature.diet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.DietPrompts
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.diet.Gap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DietSettingsViewModelTest {
    private val clock = FakeClock(1_000_000L)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val settings = SettingsRepository(db, SyncStamper(db, prefs, clock))
    private val calls = mutableListOf<String>()
    private val prompts = object : DietPrompts {
        override suspend fun reschedule() { calls += "reschedule" }
        override suspend fun cancel() { calls += "cancel" }
        override suspend fun fireNow() { calls += "fire" }
    }
    private val fmt = object : DietFormatter {
        override fun time(epochMs: Long) = "T$epochMs"
        override fun date(date: LocalDate) = date.toString()
        override fun gap(gap: Gap) = ""
    }
    private fun vm(debug: Boolean = false) = DietSettingsViewModel(settings, prefs, prompts, fmt, object : DietDebug {
        override val enabled = debug
        override fun exactAlarmsAllowed() = true
    }).tracked()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    private val vms = ViewModelStore()
    private fun <T : ViewModel> T.tracked(): T = also { vms.put("vm-${System.nanoTime()}", it) }

    @After fun tearDown() {
        // Cancel VM collectors while Main is still the test dispatcher and the database is open;
        // otherwise a late emission resumes on the real (absent) Main and fails a later test.
        vms.clear()
        Dispatchers.resetMain()
        db.close()
    }

    @Test fun showsDefaultsAndTheNextCheckIn() = runBlocking {
        prefs.setNextPromptAt(42L)
        val v = vm()
        v.uiState.test {
            var s = awaitItem()
            while (s.nextCheckIn == null) s = awaitItem()
            assertTrue(s.promptsEnabled)
            assertTrue(s.learnMealTimes)
            assertFalse(s.showCalories)
            assertEquals("T42", s.nextCheckIn)
            assertFalse(s.debug)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Used by the setup wizard's diet page too: turning the status line off removes it at once. */
    @Test fun statusLineSwitchSavesAndRefreshesTheNotification() = runBlocking {
        val v = DietSettingsViewModel(settings, prefs, prompts, fmt, object : DietDebug {
            override val enabled = false
            override fun exactAlarmsAllowed() = true
        }, DietStatusRefresh { calls += "status" }).tracked()
        v.setStatusNotification(false).join()
        assertFalse(settings.get(SyncedKeys.DietStatusNotification))
        assertEquals(listOf("status"), calls)
        v.uiState.test {
            var s = awaitItem()
            while (s.statusNotification) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun togglingCheckInsSavesAndReplans() = runBlocking {
        val v = vm()
        v.setPromptsEnabled(false).join()
        assertFalse(settings.get(SyncedKeys.DietPromptsEnabled))
        v.setLearnMealTimes(false).join()
        assertFalse(settings.get(SyncedKeys.DietLearnMealTimes))
        v.setShowCalories(true).join()
        assertTrue(settings.get(SyncedKeys.DietShowCalories))
        assertEquals(listOf("reschedule", "reschedule"), calls)
    }

    @Test fun calorieGoalIsDigitsCappedAndBlankClears() = runBlocking {
        val v = vm()
        v.setCalorieGoal("2000").join()
        assertEquals(2000, settings.get(SyncedKeys.DietCalorieGoal))
        assertEquals(2000, v.uiState.first { it.calorieGoal == 2000 }.calorieGoal)
        v.setCalorieGoal("").join()
        assertEquals(0, settings.get(SyncedKeys.DietCalorieGoal))
        v.setCalorieGoal("99999").join()
        assertEquals(10_000, settings.get(SyncedKeys.DietCalorieGoal))
        v.setCalorieGoal("1,800 kcal").join()
        assertEquals(1800, settings.get(SyncedKeys.DietCalorieGoal))
    }

    @Test fun noNextCheckInWhenOff() = runBlocking {
        prefs.setNextPromptAt(42L)
        settings.set(SyncedKeys.DietPromptsEnabled, false)
        val s = vm().uiState.first { !it.promptsEnabled }
        assertNull(s.nextCheckIn)
    }

    /** M11: "Show today's list after a long break", on by default, in settings and the setup wizard. */
    @Test fun unlockSheetSwitchDefaultsOnAndSaves() = runBlocking {
        val v = vm()
        assertTrue(v.uiState.first().unlockSheet)
        v.setUnlockSheet(false).join()
        assertFalse(settings.get(SyncedKeys.DietUnlockSheet))
        assertFalse(v.uiState.first { !it.unlockSheet }.unlockSheet)
    }

    @Test fun pretendLongBreakIsDebugOnly() = runBlocking {
        vm(debug = false).pretendLongBreak().join()
        assertFalse(prefs.unlockSheetPretend.first())
        val v = vm(debug = true)
        v.pretendLongBreak().join()
        assertTrue(prefs.unlockSheetPretend.first())
        assertTrue(v.uiState.first { it.pretendArmed }.pretendArmed)
    }

    @Test fun debugActionsFireAndDescribe() = runBlocking {
        prefs.setNextPromptAt(42L)
        val v = vm(debug = true)
        v.fireNow().join()
        assertEquals(listOf("fire"), calls)
        v.showNext().join()
        val s = v.uiState.first { it.debugInfo != null }
        assertTrue(s.debug)
        assertTrue(s.debugInfo!!, s.debugInfo!!.contains("T42"))
    }
}
