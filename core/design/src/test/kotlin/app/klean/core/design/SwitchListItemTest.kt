package app.klean.core.design

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.SwitchListItem
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwitchListItemTest {
    @get:Rule val compose = createComposeRule()

    private val switchRow = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test fun tappingTheLabelTogglesTheWholeRow() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            KleanTheme {
                var on by remember { mutableStateOf(false) }
                SwitchListItem(
                    checked = on,
                    onCheckedChange = { on = it; changes += it },
                    headline = { Text("Check-ins") },
                    supporting = { Text("About every 6 hours") },
                )
            }
        }
        // The label and summary merge into the row, which is one Switch for TalkBack.
        val row = compose.onNode(switchRow and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        row.assertIsOff().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Check-ins", useUnmergedTree = true).performClick()
        row.assertIsOn()
        compose.onNodeWithText("About every 6 hours", useUnmergedTree = true).performClick()
        row.assertIsOff()
        assertEquals(listOf(true, false), changes)
    }

    @Test fun disabledRowDoesNotToggle() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            KleanTheme {
                SwitchListItem(checked = false, onCheckedChange = { changes += it }, headline = { Text("Off") }, enabled = false)
            }
        }
        compose.onNodeWithText("Off", useUnmergedTree = true).performClick()
        assertEquals(emptyList<Boolean>(), changes)
    }
}
