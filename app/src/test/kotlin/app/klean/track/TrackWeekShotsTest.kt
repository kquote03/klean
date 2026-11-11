package app.klean.track

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.GroupChrome
import app.klean.core.design.component.GroupChromeResolver
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.component.LocalGroupChrome
import app.klean.core.design.component.MenuAction
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.clutterCount
import app.klean.core.testing.shot
import app.klean.core.ui.module.WeekSummary
import app.klean.shared.module.ModuleId
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Track › Week in light and dark (src/test/snapshots/track-week-*.png): typical, without sleep, and empty. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackWeekShotsTest {
    @get:Rule val compose = createComposeRule()

    private val days = lastSevenDays(LocalDate(2026, 11, 10))
    private val sleep = WeekSummary(
        ModuleId.SLEEP, "Sleep", "7h 05m of sleep", "on average, over 6 nights",
        bars = listOf(0.7f, 0.8f, null, 0.9f, 0.75f, 0.7f, 0.85f), barsDescription = "Sleep over the last 7 nights",
    )
    private val workout = WeekSummary(
        ModuleId.WORKOUT, "Workouts", "2 sessions", "75 min, last on Thursday",
        bars = listOf(null, 0.9f, null, null, null, 0.5f, null),
    )
    private val diet = WeekSummary(ModuleId.DIET, "Food today", "1 entry", "Oatmeal, banana at 8:10")

    @Composable
    private fun InTrack(content: @Composable () -> Unit) = CompositionLocalProvider(
        LocalGroupChrome provides GroupChromeResolver {
            GroupChrome("Track", listOf("Week", "Workout", "Sleep", "Diet"), 0, {}, listOf(MenuAction("Settings") {}))
        },
        LocalAddMenu provides AddMenuSpec(listOf(AddMenuItem("diet", "Log food", Icons.Rounded.EditNote) {}), onCapture = {}),
        content = content,
    )

    private fun render(name: String, summaries: List<WeekSummary>) = compose.shot(name, screen = true) {
        InTrack { TrackWeekContent(TrackWeekState(days, summaries, loaded = true)) }
    }

    @Test fun typical() {
        // Providers come in module-id order (diet, sleep, workout); Sleep still leads.
        render("track-week-typical", listOf(diet, sleep, workout))
        compose.onNodeWithText("This week").assertExists()
        compose.onNodeWithText("7h 05m of sleep").assertExists()
        compose.onNodeWithText("2 sessions").assertExists()
        // Overflow, the tab toolbar (one region, like the bar), Add: the cards only inform.
        assertEquals(3, compose.clutterCount().tappable)
        compose.assertClutterBudget()
    }

    @Test fun withoutSleepWorkoutsLead() {
        render("track-week-no-sleep", listOf(diet, workout))
        compose.onNodeWithText("2 sessions").assertExists()
        compose.onNodeWithText("Food today").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        render("track-week-empty", emptyList())
        compose.onNodeWithText("Your week shows up here as you log sleep, workouts or food.").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun heroIsTheFirstTrackerWithBars() {
        val s = TrackWeekState(days, listOf(diet, workout, sleep), loaded = true)
        assertEquals(ModuleId.SLEEP, s.hero?.moduleId)
        assertEquals(listOf(ModuleId.WORKOUT, ModuleId.DIET), s.rest.map { it.moduleId })
        assertEquals(LocalDate(2026, 11, 4), days.first())
        assertEquals(LocalDate(2026, 11, 10), days.last())
    }
}
