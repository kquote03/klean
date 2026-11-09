package app.klean.feature.diet.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.voice.VoiceUiState
import app.klean.core.design.theme.KleanTheme
import app.klean.feature.diet.ui.today.DietRow
import app.klean.feature.diet.ui.today.DietScreenContent
import app.klean.feature.diet.ui.today.DietUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Task 13.2: every tappable thing on the Diet screen has a spoken label and a ≥48 dp target. */
@RunWith(AndroidJUnit4::class)
class DietScreenA11yTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappablesAreLabelledAndLargeEnough() {
        val state = DietUiState(
            rows = listOf(
                DietRow.Entry("e1", "8:15 AM", "tea and toast", emptyList(), null, null),
                DietRow.GapRow("6 hours", 1L, 2L),
                DietRow.Entry("e2", "2:30 PM", "shawarma", emptyList(), null, null),
            ),
            repeats = listOf("tea and toast"),
            loaded = true,
        )
        compose.setContent {
            KleanTheme {
                DietScreenContent(
                    state = state, text = "", voice = VoiceUiState(), onTextChange = {}, onSubmit = {}, onRepeat = {},
                    onOpenEntry = {}, onOpenHistory = {}, onAddForGap = {}, onMic = {}, onUndoVoice = {},
                )
            }
        }
        // Touch bounds, not layout bounds: M3 icon buttons draw 40 dp and pad their touch target to 48 dp.
        val history = compose.onNodeWithContentDescription("History").fetchSemanticsNode().touchBoundsInRoot
        with(compose.density) { assertTrue(history.width.toDp() >= 47.5.dp && history.height.toDp() >= 47.5.dp) }
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(clickables.size >= 6)
        clickables.forEachIndexed { i, node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: node.children.firstNotNullOfOrNull { c ->
                    c.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                        ?: c.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                }
            assertTrue("clickable #$i has no label: ${node.config}", !label.isNullOrBlank() || node.config.contains(SemanticsProperties.EditableText))
            val h = with(compose.density) { node.touchBoundsInRoot.height.toDp() }
            val w = with(compose.density) { node.touchBoundsInRoot.width.toDp() }
            assertTrue("clickable '$label' is ${w}x$h", h >= 47.5.dp && w >= 47.5.dp)
        }
    }

}
