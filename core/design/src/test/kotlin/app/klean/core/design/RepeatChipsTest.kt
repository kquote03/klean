package app.klean.core.design

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.RepeatChips
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepeatChipsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachChipSaysLogAgainAndLogsOnOneTap() {
        val tapped = mutableListOf<String>()
        compose.setContent { KleanTheme { RepeatChips(listOf("tea", "toast"), onRepeat = { tapped += it }) } }
        compose.onNodeWithContentDescription("Log tea again").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Log toast again").performClick()
        assertEquals(listOf("tea", "toast"), tapped)
    }

    @Test fun showsAtMostFiveChips() {
        val items = (1..8).map { "item $it" }
        compose.setContent { KleanTheme { RepeatChips(items, onRepeat = {}) } }
        compose.onNodeWithContentDescription("Log item 5 again").assertExists()
        compose.onNodeWithContentDescription("Log item 6 again").assertDoesNotExist()
    }

    @Test fun neverGrowsBeyondTwoRows() {
        val long = listOf(
            "Popeyes Cajun sandwich", "Greek yogurt and berries", "chicken Tikka sandwich 180g",
            "2 small cheese cubes", "Popeyes wrap",
        )
        compose.setContent {
            KleanTheme { RepeatChips(long, onRepeat = {}, modifier = Modifier.width(60.dp).testTag("chips")) }
        }
        // 60 dp fits one chip per row (Robolectric measures text near zero wide), so five rows unless capped.
        // Two 48 dp rows plus one 8 dp gap.
        val bounds = compose.onNodeWithTag("chips").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("chips are $height tall", height <= 104.dp)
    }
}
