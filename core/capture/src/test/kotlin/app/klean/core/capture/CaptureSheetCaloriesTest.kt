package app.klean.core.capture

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.voice.VoiceUiState
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The optional kcal slot (plan 2026-11-11 calories, phase 5): only for Food with Show calories on. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureSheetCaloriesTest {
    @get:Rule val compose = createComposeRule()

    private fun content(slot: Boolean, initial: String = "") {
        compose.setContent { Sheet(slot, initial) }
    }

    @androidx.compose.runtime.Composable
    private fun Sheet(slot: Boolean, initial: String) {
        var kcal by androidx.compose.runtime.remember { mutableStateOf(initial) }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            CaptureSheetContent(
                state = CaptureUiState(text = "chips", showCalorieSlot = slot, calories = kcal, suggestions = listOf("Oatmeal", "Coffee")),
                voice = VoiceUiState(),
                onTextChange = {}, onSubmit = {}, onMove = {}, onMic = {}, onUndoVoice = {},
                photoPicker = null, onRemovePhoto = {}, onEatenTimePicked = { _, _ -> },
                onCaloriesChange = { kcal = it },
            )
        }
    }

    @Test fun theSlotShowsOnlyWhenAsked() {
        content(slot = false)
        compose.onAllNodesWithTag(KCAL_TAG).assertCountEquals(0)
    }

    @Test fun previewFollowsWhatIsTyped() {
        content(slot = true)
        compose.onNodeWithText("kcal").assertExists()
        compose.onNodeWithTag(KCAL_TAG).performTextInput("150x100x200")
        compose.onNodeWithText("= 300 kcal").assertExists()
        compose.onNodeWithTag(KCAL_TAG).performTextReplacement("200g")
        compose.onNodeWithText("200 g · Klean will fill in the calories").assertExists()
        compose.onNodeWithTag(KCAL_TAG).performTextReplacement("lots")
        compose.onNodeWithText("Not sure what that means").assertExists()
    }

    @Test fun theInfoButtonExplainsTheSyntax() {
        content(slot = true)
        compose.onNodeWithContentDescription("How to write calories").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("150x100x200 = 150 kcal per 100 g", substring = true).assertExists()
    }

    @Test fun staysWithinTheFormBudget() {
        content(slot = true)
        compose.assertClutterBudget(maxInteractive = 7, maxTextInputs = 2)
    }

    @Test fun shot() {
        compose.shot("capture-kcal") { Sheet(true, "150x100x200") }
    }
}
