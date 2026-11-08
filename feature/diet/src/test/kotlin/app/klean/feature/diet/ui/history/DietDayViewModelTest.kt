package app.klean.feature.diet.ui.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.FakePhotos
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.today.DietRow
import app.klean.shared.time.atMinute
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DietDayViewModelTest {
    private val nov7 = LocalDate(2026, 11, 7)
    private val nov12 = LocalDate(2026, 11, 12)
    private val clock = FakeClock(atMinute(nov12, 15 * 60, Dubai))
    private val repo = FakeDietRepository(clock)
    private val fmt = AndroidDietFormatter(ApplicationProvider.getApplicationContext<Context>(), DubaiZone)
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(date: LocalDate = nov7) =
        DietDayViewModel(SavedStateHandle(mapOf("isoDate" to date.toString())), repo, clock, fmt, oneTick, FakePhotos())

    @Test fun pagerStartsOnTheRequestedDayAndEndsToday() {
        val vm = vm()
        assertEquals(nov7, vm.dateForPage(vm.initialPage))
        assertEquals(nov12, vm.dateForPage(vm.pageCount - 1))
        assertEquals(vm.pageCount - 6, vm.initialPage)
        // A date in the future opens on today.
        val future = vm(LocalDate(2026, 12, 1))
        assertEquals(nov12, future.dateForPage(future.initialPage))
    }

    @Test fun pastDayUsesTheSameRowsWithGapsEndingAtTheDayEnd() = runTest {
        val breakfast = atMinute(nov7, 9 * 60, Dubai)
        repo.entries.value = listOf(repo.entry("a", "porridge", breakfast))
        val page = vm().day(nov7).first { it.loaded }
        assertEquals(fmt.date(nov7), page.title)
        val entry = page.rows[0] as DietRow.Entry
        assertEquals("porridge", entry.text)
        val gap = page.rows[1] as DietRow.GapRow
        assertEquals(breakfast, gap.startMs)
        assertFalse("past-day gaps are closed, never 'Nothing since'", gap.label.startsWith("Nothing since"))
        assertTrue(page.hasEntries)
    }

    @Test fun emptyPastDayHasNoEntries() = runTest {
        val page = vm().day(LocalDate(2026, 11, 5)).first { it.loaded }
        assertFalse(page.hasEntries)
    }

    @Test fun undoAfterRemovingFromTheEntryScreenRestores() {
        vm().undoRemove("x")
        assertEquals(listOf("x"), repo.restores)
    }
}
