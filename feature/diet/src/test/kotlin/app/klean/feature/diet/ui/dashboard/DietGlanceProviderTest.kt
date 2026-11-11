package app.klean.feature.diet.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ui.module.QuickAction
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.Gap
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diet on Today's whiteboard: the foods themselves with their times, never "3 entries". */
@RunWith(AndroidJUnit4::class)
class DietGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 11, day, h, m).toInstant(Dubai).toEpochMilliseconds()

    private val clock = FakeClock(at(11, 14))
    private val repo = FakeDietRepository(clock)
    private val fmt = object : DietFormatter {
        override fun time(epochMs: Long) = "T$epochMs"
        override fun date(date: LocalDate) = date.toString()
        override fun gap(gap: Gap) = ""
    }
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }
    private val promptsOn = MutableStateFlow(false)
    private val next = MutableStateFlow<Long?>(null)
    private fun provider() = DietGlanceProvider(ctx, repo, fmt, oneTick, { next }, { promptsOn })

    @Test fun everyFoodTodayByNameAndTime() = runTest {
        repo.logEntry("coffee with milk", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 8, 10))
        repo.logEntry("shawarma", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 13, 15))
        repo.logEntry("dinner yesterday", CaptureSource.MODULE_SCREEN, eatenAt = at(10, 20))
        val g = provider().glance().first()
        assertEquals(setOf("coffee with milk", "shawarma"), g.done.map { it.text }.toSet())
        assertEquals(at(11, 13, 15), g.done.first { it.text == "shawarma" }.at)
        assertTrue(g.done.all { it.module == ModuleId.DIET && it.route == "klean://diet" })
        assertTrue(g.attention.isEmpty())
        assertFalse((g.done.map { it.text } + g.done.mapNotNull { it.detail }).any { Regex("\\d+ entr").containsMatchIn(it) })
    }

    @Test fun itemsAndCaloriesAreTheDetail() = runTest {
        val id = repo.logEntry("shawarma plate", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 13))
        val plain = repo.logEntry("banana", CaptureSource.MODULE_SCREEN, eatenAt = at(11, 10))
        repo.items.value = listOf(
            repo.item("i1", id, 0, "chicken", "1 wrap", 450),
            repo.item("i2", id, 1, "garlic sauce", null, 80),
            repo.item("i3", plain, 0, "banana", "1", 100),
        )
        val rows = provider().glance().first().done.associateBy { it.text }
        assertEquals("chicken, garlic sauce", rows.getValue("shawarma plate").detail)
        assertNull(rows.getValue("banana").detail)
        repo.prefs = repo.prefs.copy(showCalories = true)
        val shown = provider().glance().first().done.associateBy { it.text }
        assertEquals("chicken, garlic sauce · ~530 kcal", shown.getValue("shawarma plate").detail)
        assertEquals("~100 kcal", shown.getValue("banana").detail)
    }

    @Test fun nextCheckInIsComingUpOnlyWhenOn() = runTest {
        next.value = at(11, 17)
        assertTrue(provider().glance().first().upcoming.isEmpty())
        promptsOn.value = true
        val row = provider().glance().first().upcoming.single()
        assertEquals("Check-in around T${at(11, 17)}", row.text)
        assertEquals(at(11, 17), row.at)
    }

    @Test fun foodIsOneTapCapture() = runTest {
        val a = provider().glance().first().quickAction
        assertEquals(QuickAction.Capture(ModuleId.DIET, "Food", a!!.icon), a)
        val g = provider().glance().first()
        assertFalse((listOf(a.label) + g.upcoming.map { it.text }).any { "!" in it })
    }
}
