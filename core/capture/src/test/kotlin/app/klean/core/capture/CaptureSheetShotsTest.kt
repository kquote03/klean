package app.klean.core.capture

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.voice.VoiceUiState
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.shared.capture.CandidateAction
import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The capture sheet the Add menu opens ("Type or speak…" or a module's line): typing, and "Saved to Diet" with the
 * M28 "Move to…" choices. Light and dark in src/test/snapshots; one field, at most one chip row.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureSheetShotsTest {
    @get:Rule val compose = createComposeRule()

    // Module names live in the feature modules; without them the sheet shows the module id, which is fine for a fixture.
    private fun option(id: ModuleId) = MoveOption(CandidateAction(id, "x", 0.5f), null)

    private fun render(name: String, state: CaptureUiState, onMove: (MoveOption) -> Unit = {}) = compose.shot(name) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            CaptureSheetContent(
                state = state,
                voice = VoiceUiState(),
                onTextChange = {},
                onSubmit = {},
                onMove = onMove,
                onMic = {},
                onUndoVoice = {},
                photoPicker = null,
                onRemovePhoto = {},
                onEatenTimePicked = { _, _ -> },
            )
        }
    }

    @Test fun typing() {
        render("capture-typing", CaptureUiState(text = "Lamb kofta wrap", suggestions = listOf("Oatmeal", "Coffee")))
        compose.assertClutterBudget(maxTextInputs = 1)
    }

    @Test fun savedWithMoveTo() {
        val moved = mutableListOf<ModuleId>()
        val state = CaptureUiState(
            phase = CapturePhase.SavedWithAlternatives(
                moduleLabelRes = null,
                options = listOf(option(ModuleId.TASKS)),
                more = listOf(option(ModuleId.CALENDAR), option(ModuleId.WORKOUT)),
            ),
        )
        render("capture-saved-move", state) { moved += it.action.moduleId }
        compose.assertClutterBudget()
        compose.onNodeWithText("Move to…").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<ModuleId>(), moved)
    }
}
