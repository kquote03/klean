package app.klean.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.nav.NavGroup
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today in light and dark: empty, typical and long (src/test/snapshots/today-*.png), each within the clutter budget. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayShotsTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate(2026, 11, 10)
    private val add = AddMenuSpec(
        listOf(
            AddMenuItem("diet", "Log food", Icons.Rounded.EditNote) {},
            AddMenuItem("workout", "Log workout", Icons.Rounded.FitnessCenter) {},
            AddMenuItem("tasks", "Task", Icons.Rounded.CheckCircleOutline) {},
        ),
        onCapture = {},
    )

    private val typical = listOf(
        TodayCard(
            NavGroup.PLAN, null, "Team sync", "15:00 to 15:30",
            listOf(
                TodayRow("e2", "Dinner with Lina", "19:30", null),
                TodayRow("t1", "Buy groceries", null, null, isTask = true),
                TodayRow("t2", "Book a dentist check-up", null, null, isTask = true),
            ),
        ),
        TodayCard(
            NavGroup.TRACK, "Sleep", "Last night · 7h 10m", null,
            listOf(
                TodayRow("d", "Oatmeal, banana · 8:10", null, Icons.Rounded.EditNote),
                TodayRow("w", "Last on Thursday", null, Icons.Rounded.FitnessCenter),
            ),
        ),
        TodayCard(NavGroup.PEOPLE, "Next birthday", "Sara, in 3 days", null, emptyList()),
    )

    private fun render(name: String, cards: List<TodayCard>, opened: MutableList<NavGroup> = mutableListOf()) =
        compose.shot(name, screen = true) {
            CompositionLocalProvider(LocalAddMenu provides add) {
                TodayContent(
                    state = TodayState(today = day, cards = cards, loaded = true),
                    canArrange = cards.isNotEmpty(),
                    onOpenGroup = { opened += it },
                    onArrange = {},
                    onSettings = {},
                )
            }
        }

    @Test fun typical() {
        val opened = mutableListOf<NavGroup>()
        render("today-typical", typical, opened)
        compose.onNodeWithText("Tuesday").assertExists()
        compose.onNodeWithContentDescription("Settings").assertExists()
        compose.assertClutterBudget()
        compose.onNodeWithText("Sara, in 3 days").performClick()
        assertEquals(listOf(NavGroup.PEOPLE), opened)
    }

    @Test fun empty() {
        render("today-empty", emptyList())
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun longContent() {
        val long = typical.map { c ->
            c.copy(
                fact = c.fact + " with a much longer title that wraps onto a second line",
                rows = c.rows.map { it.copy(text = it.text + ", and a note that runs past the edge") },
            )
        }
        render("today-long", long)
        compose.assertClutterBudget()
    }

    @Test fun addMenuOpen() {
        val shot = render("today-add", typical)
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Type or speak…").assertExists()
        shot.capture("add-menu-on-today")
    }
}
