package app.klean.feature.sleep.ui

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.DubaiZone
import app.klean.feature.sleep.data.Night
import app.klean.shared.time.atMinute
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Track › Sleep in light and dark: typical, asleep, empty, long, and the overflow. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepShotsTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate(2026, 11, 10)
    private val sync = SyncColumns(0, 0, "d", false, 0)

    /** The night that ended [back] days ago: to bed at [bed] minutes past 22:00 the evening before, [length] minutes long. */
    private fun night(back: Int, bed: Int, length: Int?): Night {
        val date = today.minus(DatePeriod(days = back))
        if (length == null) return Night(date, null)
        val bedAt = atMinute(date.minus(DatePeriod(days = 1)), 22 * 60 + bed, Dubai)
        return Night(date, SleepSessionEntity("n$back", bedAt, bedAt + length * 60_000L, sync = sync))
    }

    private val nights = listOf(night(0, 100, 430), night(1, 70, 410), night(2, 60, 445), night(3, 90, null), night(4, 30, 470)) +
        (5 until 14).map { night(it, 45, 400 + it * 7) }

    private fun state(nights: List<Night> = this.nights, open: SleepSessionEntity? = null) = SleepState(
        loaded = true, today = today, open = open, lastNight = nights.firstOrNull { it.date == today }?.session, nights = nights,
    )

    private fun render(name: String, s: SleepState, onToggle: () -> Unit = {}, onEdit: (String?) -> Unit = {}) = compose.shot(name, screen = true) {
        SleepContent(s, SleepFormatter(LocalContext.current, DubaiZone), onToggle = onToggle, onEdit = onEdit, onSettings = {})
    }

    @Test fun typical() {
        var toggled = 0
        val edited = mutableListOf<String?>()
        render("sleep-typical", state(nights.take(7) + (7 until 14).map { night(it, 0, null) }), onToggle = { toggled++ }, onEdit = { edited += it })
        compose.onNodeWithText("Last night").assertExists()
        compose.onNodeWithText("7 h 10 m").assertExists()
        compose.onNodeWithText("Recent nights").assertExists()
        compose.onNodeWithText("Sunday into Monday").performClick()
        assertEquals(listOf<String?>("n1"), edited)
        compose.onNodeWithText("Going to bed").performClick()
        assertEquals(1, toggled)
        compose.assertClutterBudget()
    }

    @Test fun asleep() {
        val open = SleepSessionEntity("o", atMinute(today, 23 * 60 + 40, Dubai), sync = sync)
        render("sleep-asleep", state(open = open))
        compose.onNodeWithText("Asleep").assertExists()
        compose.onNodeWithText("I'm up").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        var toggled = 0
        render("sleep-empty", state(nights = (0 until 14).map { night(it, 0, null) }), onToggle = { toggled++ })
        compose.onNodeWithText("Nights you log show up here.").assertExists()
        compose.onNodeWithText("Going to bed").performClick()
        assertEquals(1, toggled)
        compose.assertClutterBudget()
    }

    @Test fun longContent() {
        render("sleep-long", state())
        compose.assertClutterBudget()
    }

    @Test fun overflowAddsANightAndOpensSettings() {
        val shot = render("sleep-menu", state())
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add a night").assertExists()
        compose.onNodeWithText("Sleep settings").assertExists()
        shot.capture("sleep-menu-open")
    }

    @Test fun weekBarsAndAverage() {
        val w = SleepWeek.of(nights)
        assertEquals(today, w.days.last())
        assertEquals(6, w.nights)
        assertEquals(null, w.bars[3])
        assertEquals(1f, w.bars.filterNotNull().max())
    }
}
