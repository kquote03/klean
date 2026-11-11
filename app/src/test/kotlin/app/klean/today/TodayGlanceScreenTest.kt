package app.klean.today

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.theme.KleanTheme
import app.klean.core.ui.module.QuickAction
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Today as a whiteboard: the foods themselves, one tap per module, sections in a fixed order, no Add button. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class TodayGlanceScreenTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<QuickAction>()
    private var voice = 0
    private var typed = 0
    private val order = androidx.compose.runtime.mutableStateOf(emptyList<String>())
    private val add = AddMenuSpec(listOf(AddMenuItem("tasks", "Task", Icons.Rounded.CheckCircleOutline) {}), onCapture = {})

    @Before fun reset() {
        TodayFixtures.instantRuns = 0
        TodayFixtures.undone = 0
    }

    private fun render(state: TodayState = TodayFixtures.state()) = compose.setContent {
        KleanTheme {
            // The shell offers the Add menu everywhere; Today declines it.
            CompositionLocalProvider(LocalAddMenu provides add) {
                TodayContent(
                    state = state,
                    canArrange = true,
                    onQuickAction = { actions += it },
                    onVoice = { voice++ },
                    onType = { typed++ },
                    onOpen = {},
                    onArrange = {},
                    onSettings = {},
                    timeOf = TodayFixtures.timeOf,
                    buttonOrder = order.value,
                    onButtonOrder = { order.value = it },
                )
            }
        }
    }

    private fun allTexts(): List<String> =
        compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    @Test fun showsFoodNamesNotCounts() {
        render()
        compose.onNodeWithText("shawarma").assertExists()
        compose.onNodeWithText("coffee with milk").assertExists()
        assertFalse(allTexts().any { Regex("\\d+ entr").containsMatchIn(it) })
    }

    @Test fun oneTapFoodOpensTargetedCapture() {
        render()
        compose.onNodeWithText("Food").performClick()
        assertEquals(listOf(ModuleId.DIET), actions.map { it.module })
        compose.onNodeWithText("Speak").performClick()
        compose.onNodeWithText("Type").performClick()
        assertEquals(1, voice)
        assertEquals(1, typed)
    }

    @Test fun sleepIsInstant() {
        render()
        compose.onNodeWithText("Bedtime").performClick()
        compose.waitUntil(5_000) { TodayFixtures.instantRuns == 1 }
        compose.onNodeWithText("Good night").assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { TodayFixtures.undone == 1 }
        // The instant action never goes through the screen's other handlers.
        assertTrue(actions.isEmpty())
    }

    @Test fun sectionsInOrder() {
        render()
        // The day reads top down; the one-tap buttons sit below it, by the thumb.
        val tops = listOf("Needs attention", "Coming up", "So far today", "Speak").map {
            compose.onNodeWithText(it).getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
        assertEquals(tops.size, tops.distinct().size)
    }

    @Test fun noFabOnToday() {
        render()
        assertEquals(0, compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().size)
    }

    @Test fun clutterBudget() {
        render()
        compose.assertClutterBudget()
    }

    @Test fun emptyDayIsCalm() {
        render(TodayFixtures.state(TodayFixtures.empty))
        compose.onNodeWithText("Nothing yet").assertExists()
        compose.onNodeWithText("Nothing else today").assertExists()
        val words = allTexts().joinToString(" ").lowercase()
        assertFalse(Regex("\\bno\\b").containsMatchIn(words))
        assertFalse("missed" in words)
        compose.assertClutterBudget()
    }

    @Test fun arrangeButtonsSwapsTwoTapsAndRunsNothing() {
        render()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Arrange buttons").performClick()
        compose.onNodeWithText("Tap two buttons to swap them").assertExists()
        compose.onNodeWithText("Speak").performClick()
        compose.onNodeWithText("Food").performClick()
        // Food took Speak's corner and nothing ran.
        assertEquals("diet", order.value.first())
        assertEquals(0, voice)
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("Reset").performClick()
        assertTrue(order.value.isEmpty())
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Tap two buttons to swap them").assertDoesNotExist()
    }
}
