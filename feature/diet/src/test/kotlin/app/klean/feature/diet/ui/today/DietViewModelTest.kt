package app.klean.feature.diet.ui.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.atMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DietViewModelTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 18 * 60, Dubai))
    private val repo = FakeDietRepository(clock)
    private val fmt = AndroidDietFormatter(ApplicationProvider.getApplicationContext<Context>(), DubaiZone)
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DietViewModel(repo, clock, fmt, oneTick)

    @Test fun submitLogsFromModuleScreen() = runTest {
        val vm = vm()
        vm.onSubmit("  toast ")
        vm.onSubmit("   ")
        assertEquals(listOf(FakeDietRepository.LogCall("toast", CaptureSource.MODULE_SCREEN, null)), repo.logs)
        vm.onRepeat("coffee")
        assertEquals("coffee", repo.logs.last().text)
    }

    /** Found on device: after deleting an entry elsewhere, its text stayed as a one-tap chip here. */
    @Test fun repeatChipsFollowEntryChangesMadeElsewhere() = runTest {
        val t = atMinute(nov9, 12 * 60, Dubai)
        repo.entries.value = listOf(repo.entry("a", "widget test", t), repo.entry("b", "tea", t + 60_000))
        repo.repeats = listOf("widget test", "tea")
        val vm = vm()
        vm.uiState.test {
            var s = awaitItem()
            while (s.repeats != listOf("widget test", "tea")) s = awaitItem()
            repo.repeats = listOf("tea")
            repo.delete("a") // e.g. from the entry screen or the capture sheet's undo
            while (s.repeats != listOf("tea")) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rowsInterleaveNeutralGapBetweenEntries() = runTest {
        val a = atMinute(nov9, 10 * 60 + 30, Dubai)
        val b = atMinute(nov9, 17 * 60, Dubai)
        repo.entries.value = listOf(repo.entry("a", "porridge", a), repo.entry("b", "rice and dal", b, photo = "p.jpg"))
        repo.repeats = listOf("coffee")
        val vm = vm()
        vm.uiState.test {
            var s = awaitItem()
            while (s.rows.size < 3) s = awaitItem()
            assertEquals(nov9, s.date)
            assertEquals(listOf("coffee"), s.repeats)
            val rows = s.rows
            assertTrue(rows[0] is DietRow.Entry && (rows[0] as DietRow.Entry).id == "a")
            val gap = rows[1] as DietRow.GapRow
            assertEquals(a, gap.startMs)
            assertTrue(rows[2] is DietRow.Entry && (rows[2] as DietRow.Entry).hasPhoto)
            assertEquals("No entries ${fmt.time(a)}–${fmt.time(b)}", gap.label)
            assertTrue((rows[0] as DietRow.Entry).timeLabel.isNotBlank())
            rows.filterIsInstance<DietRow.GapRow>().forEach {
                assertFalse(it.label, Regex("(?i)missed|behind|streak|failed").containsMatchIn(it.label))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun ongoingGapSaysNothingSince() = runTest {
        val a = atMinute(nov9, 9 * 60, Dubai)
        repo.entries.value = listOf(repo.entry("a", "porridge", a))
        val vm = vm()
        vm.uiState.test {
            var s = awaitItem()
            while (s.rows.size < 2) s = awaitItem()
            val gap = s.rows[1] as DietRow.GapRow
            assertEquals("Nothing since ${fmt.time(a)}", gap.label)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
