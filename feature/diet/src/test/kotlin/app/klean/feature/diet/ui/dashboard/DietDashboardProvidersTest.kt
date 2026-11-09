package app.klean.feature.diet.ui.dashboard

import app.klean.feature.diet.DietDayRoute
import app.klean.feature.diet.DietRoute
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.Gap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DietDashboardProvidersTest {
    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDateTime(2026, 11, day, h, m).toInstant(Dubai).toEpochMilliseconds()

    private val clock = FakeClock(at(11, 14))
    private val repo = FakeDietRepository(clock)
    private val fmt = object : DietFormatter {
        override fun time(epochMs: Long) = "T$epochMs"
        override fun date(date: LocalDate) = date.toString()
        override fun gap(gap: Gap) = ""
    }
    private val text = object : DietSummaryText {
        override fun todayLabel() = "Today"
        override fun count(n: Int) = when (n) { 0 -> "No entries yet"; 1 -> "1 entry"; else -> "$n entries" }
        override fun lastLabel() = "Last entry"
        override fun last(text: String, time: String) = "$text · $time"
        override fun nextLabel() = "Next check-in"
        override fun next(time: String) = "around $time"
        override fun kcalLabel() = "So far"
    }
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }
    private val promptsOn = MutableStateFlow(false)
    private val next = MutableStateFlow<Long?>(null)

    private fun providers() = DietDashboardProviders(repo, fmt, text, oneTick, DubaiZone, { next }, { promptsOn })

    @Test fun noEntriesShowsTodayTileOnly() = runTest {
        val tiles = providers().summaries().first()
        assertEquals(listOf("diet.today"), tiles.map { it.key })
        assertEquals("No entries yet", tiles.single().value)
        assertEquals("Today", tiles.single().label)
        assertEquals(DietRoute, tiles.single().route)
    }

    @Test fun todayCountAndLastEntry() = runTest {
        repo.logEntry("toast", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 8, 10))
        repo.logEntry("shawarma", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 13, 15))
        repo.logEntry("dinner yesterday", CaptureSource.MODULE_SCREEN, eatenAt = at(10, 20))
        val tiles = providers().summaries().first()
        assertEquals(listOf("diet.today", "diet.last"), tiles.map { it.key })
        assertEquals("2 entries", tiles[0].value)
        assertEquals("shawarma · T${at(11, 13, 15)}", tiles[1].value)
        assertEquals("Last entry", tiles[1].label)
    }

    @Test fun longLastEntryIsShortened() = runTest {
        repo.logEntry("a".repeat(60), CaptureSource.MODULE_SCREEN, eatenAt = at(11, 9))
        val last = providers().summaries().first().first { it.key == "diet.last" }
        assertEquals("a".repeat(39) + "…" + " · T${at(11, 9)}", last.value)
    }

    @Test fun nextCheckInOnlyWhenOn() = runTest {
        val p = providers()
        next.value = at(11, 17)
        assertEquals(listOf("diet.today"), p.summaries().first().map { it.key })
        promptsOn.value = true
        val tiles = p.summaries().first()
        assertEquals(listOf("diet.today", "diet.next"), tiles.map { it.key })
        assertEquals("around T${at(11, 17)}", tiles[1].value)
        next.value = null
        assertEquals(listOf("diet.today"), p.summaries().first().map { it.key })
    }

    @Test fun caloriesOnlyWhenShown() = runTest {
        val id = repo.logEntry("shawarma and fries", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 13))
        repo.items.value = listOf(
            repo.item("i1", id, 0, "shawarma", "1 wrap", 450),
            repo.item("i2", id, 1, "fries", "medium", 320),
            repo.item("i3", "other", 0, "cake", null, 999),
        )
        assertNull(providers().summaries().first().firstOrNull { it.key == "diet.kcal" })
        repo.prefs = repo.prefs.copy(showCalories = true)
        val kcal = providers().summaries().first().first { it.key == "diet.kcal" }
        assertEquals("~770 kcal", kcal.value)
        assertEquals("So far", kcal.label)
        repo.rejectItem("i2")
        assertEquals("~450 kcal", providers().summaries().first().first { it.key == "diet.kcal" }.value)
    }

    @Test fun weekActivityCountsByLogicalDay() = runTest {
        repo.logEntry("late snack", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 1, 30))
        repo.logEntry("breakfast", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 10))
        repo.logEntry("last week", CaptureSource.MODULE_SCREEN, eatenAt = at(3, 10))
        val p = providers()
        val week = p.activity(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15)).first()
        assertEquals(mapOf(LocalDate(2026, 11, 10) to 1, LocalDate(2026, 11, 11) to 1), week)
        assertEquals(DietRoute, p.dayRoute(LocalDate(2026, 11, 11)))
        assertEquals(DietDayRoute("2026-11-10"), p.dayRoute(LocalDate(2026, 11, 10)))
    }
}
