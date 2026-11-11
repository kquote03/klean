package app.klean.core.design.component

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import app.klean.core.design.theme.Space
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A card's action button keeps its distance from the tinted hero (seen on Track › Sleep: \"Going to bed\" sat flush). */
@RunWith(AndroidJUnit4::class)
class ModuleCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionButtonClearsTheHero() {
        compose.setContent {
            KleanTheme {
                ModuleCard(label = "Last night", fact = "7h 10m", supporting = "23:40 to 06:50", action = MenuAction("Going to bed") {})
            }
        }
        val heroBottom: Dp = compose.onNodeWithText("23:40 to 06:50").getUnclippedBoundsInRoot().bottom
        val buttonTop: Dp = compose.onNode(hasText("Going to bed") and hasClickAction()).getUnclippedBoundsInRoot().top
        // The hero's own bottom padding (Space.md), then the button's top padding (Space.md).
        assertTrue("gap ${buttonTop - heroBottom}", buttonTop - heroBottom >= Space.md * 2)
    }
}
