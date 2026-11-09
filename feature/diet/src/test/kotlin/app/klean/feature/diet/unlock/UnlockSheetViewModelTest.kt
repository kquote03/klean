package app.klean.feature.diet.unlock

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.Gap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UnlockSheetViewModelTest {
    private val clock = FakeClock(EngineRig.t(14))
    private val repo = FakeDietRepository(clock)
    private val fmt = object : DietFormatter {
        override fun time(epochMs: Long) = "T$epochMs"
        override fun date(date: LocalDate) = date.toString()
        override fun gap(gap: Gap) = ""
    }
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun showsTodayNewestFirstWithRepeats() = runTest {
        repo.logEntry("porridge", CaptureSource.MODULE_SCREEN, eatenAt = EngineRig.t(9))
        repo.logEntry("wrap", CaptureSource.MODULE_SCREEN, eatenAt = EngineRig.t(13))
        repo.repeats = listOf("tea")
        val vm = UnlockSheetViewModel(repo, fmt, oneTick)
        val s = vm.uiState.first { it.entries.isNotEmpty() }
        assertEquals(listOf("wrap", "porridge"), s.entries.map { it.text })
        assertEquals("T${EngineRig.t(13)}", s.entries.first().timeLabel)
        assertEquals(listOf("tea"), s.suggestions)
    }

    @Test fun logsWithTheUnlockSheetSourceAndIgnoresBlanks() = runTest {
        val vm = UnlockSheetViewModel(repo, fmt, oneTick)
        vm.log("   ")
        vm.log(" shawarma ").join()
        assertEquals(listOf(FakeDietRepository.LogCall("shawarma", CaptureSource.UNLOCK_SHEET, null)), repo.logs)
        assertTrue(vm.uiState.first { it.entries.isNotEmpty() }.entries.any { it.text == "shawarma" })
    }
}
