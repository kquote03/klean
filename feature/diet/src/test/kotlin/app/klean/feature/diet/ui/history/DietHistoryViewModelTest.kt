package app.klean.feature.diet.ui.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DietHistoryViewModelTest {
    private val nov12 = LocalDate(2026, 11, 12)
    private val clock = FakeClock(atMinute(nov12, 15 * 60, Dubai))
    private val repo = FakeDietRepository(clock)

    private fun vm() = DietHistoryViewModel(repo, DubaiZone)

    /** Sleep window 00:00–08:00 puts the logical day boundary at 04:00. */
    @Test fun countsGroupByLogicalDayAcrossTheFourOClockBoundary() = runTest {
        val nov9 = LocalDate(2026, 11, 9)
        val nov10 = LocalDate(2026, 11, 10)
        repo.entries.value = listOf(
            repo.entry("a", "dinner", atMinute(nov9, 21 * 60, Dubai)),
            repo.entry("b", "late snack", atMinute(nov10, 3 * 60 + 30, Dubai)), // still Nov 9
            repo.entry("c", "early tea", atMinute(nov10, 4 * 60 + 30, Dubai)), // Nov 10
            repo.entry("d", "lunch", atMinute(nov10, 13 * 60, Dubai)),
            repo.entry("e", "oct", atMinute(LocalDate(2026, 11, 1), 2 * 60, Dubai)), // logical Oct 31
            repo.entry("f", "removed", atMinute(nov10, 14 * 60, Dubai)).let { it.copy(sync = it.sync.copy(deleted = true)) },
        )
        val m = vm().month(LocalDate(2026, 11, 1)).first { it.loaded }
        assertEquals(30, m.days.size)
        val counts = m.days.associate { it.date to it.count }
        assertEquals(2, counts[nov9])
        assertEquals(2, counts[nov10])
        assertEquals(0, counts[LocalDate(2026, 11, 1)])
        assertEquals(4, counts.values.sum()) // Oct 31 and the tombstone are not in November
        assertTrue(m.days.single { it.date == nov12 }.isToday)
        assertTrue(m.days.single { it.date == LocalDate(2026, 11, 13) }.isFuture)
        assertFalse(m.days.single { it.date == nov12 }.isFuture)
        assertFalse(m.canGoNext)

        val oct = vm().month(LocalDate(2026, 10, 1)).first { it.loaded }
        assertEquals(1, oct.days.single { it.date == LocalDate(2026, 10, 31) }.count)
        assertTrue(oct.canGoNext)
    }

    @Test fun pagesEndAtTheCurrentMonth() {
        val vm = vm()
        assertEquals(LocalDate(2026, 11, 1), vm.monthForPage(vm.pageCount - 1))
        assertEquals(LocalDate(2026, 10, 1), vm.monthForPage(vm.pageCount - 2))
        assertEquals(LocalDate(2025, 12, 1), vm.monthForPage(vm.pageCount - 12))
    }
}
