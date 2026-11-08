package app.klean.feature.diet.widget

import app.cash.turbine.test
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DietWidgetStateTest {
    private val noon = atMinute(LocalDate(2026, 11, 9), 12 * 60, Dubai)
    private val clock = FakeClock(noon)
    private val repo = FakeDietRepository(clock)
    private val disabled = MutableStateFlow(emptySet<ModuleId>())
    private val highlight = MutableStateFlow<String?>(null)
    private val states = dietWidgetStates(repo, disabled, highlight) { "t$it" }

    @Test fun showsTodayNewestFirstAndFollowsWrites() = runTest {
        repo.entries.value = listOf(
            repo.entry("a", "toast", noon - 3_600_000),
            repo.entry("old", "yesterday soup", noon - 30 * 3_600_000),
        )
        states.test {
            val first = awaitItem()
            assertEquals(1, first.count)
            assertEquals("toast", first.lastText)
            repo.logEntry("tea", CaptureSource.WIDGET)
            val next = awaitItem()
            assertEquals(2, next.count)
            assertEquals(listOf("tea", "toast"), next.entries.map { it.text })
            assertEquals("t$noon", next.entries.first().time)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun disabledDietShowsOnlyTheDisabledState() = runTest {
        repo.entries.value = listOf(repo.entry("a", "toast", noon - 60_000))
        states.test {
            assertTrue(awaitItem().dietEnabled)
            disabled.value = setOf(ModuleId.DIET)
            val off = awaitItem()
            assertFalse(off.dietEnabled)
            assertTrue(off.entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun pendingCheckInHighlightsThePill() = runTest {
        states.test {
            assertFalse(awaitItem().highlighted)
            highlight.value = "prompt-1"
            assertTrue(awaitItem().highlighted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun highlightShowsTheCheckInWording() = runTest {
        val labelled = dietWidgetStates(repo, disabled, highlight, highlightLabel = { if (it == "p2") "Quick food note?" else null }) { "t$it" }
        labelled.test {
            assertEquals(null, awaitItem().highlightText)
            highlight.value = "p2"
            val on = awaitItem()
            assertTrue(on.highlighted)
            assertEquals("Quick food note?", on.highlightText)
            highlight.value = null
            assertEquals(null, awaitItem().highlightText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
