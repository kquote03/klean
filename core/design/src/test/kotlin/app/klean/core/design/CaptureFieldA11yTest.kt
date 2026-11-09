package app.klean.core.design

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.CaptureField
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility checks for the capture field (Task 13.2). TalkBack isn't available on the test
 * device, so labels and target sizes are checked through Compose semantics instead.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFieldA11yTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyButtonHasALabelAndAFullSizeTarget() {
        compose.setContent {
            KleanTheme { CaptureField("", {}, {}, {}, onPhoto = {}, placeholder = "What did you eat?") }
        }
        listOf("Add a photo", "Speak").forEach {
            compose.onNodeWithContentDescription(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription("Save").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
    }

    /** Found on Waydroid at font scale 2.0: the placeholder was cut to "What did y…". */
    @Test fun placeholderStaysReadableAtDoubleFontSize() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                KleanTheme {
                    CaptureField(
                        "", {}, {}, {}, onPhoto = null, placeholder = "What did you eat?",
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("What did you eat?", useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        // Robolectric doesn't measure real glyphs, so check the line limit instead of the rendered overflow.
        val l = layouts.single()
        assertTrue("placeholder is limited to ${l.layoutInput.maxLines} line(s)", l.layoutInput.maxLines > 2)
    }

    @Test fun newlineFromAKeyboardSubmitsInsteadOfBreakingTheLine() {
        var text = ""
        var submitted = 0
        compose.setContent {
            KleanTheme { CaptureField(text, { text = it }, { submitted++ }, {}, onPhoto = null, placeholder = "p") }
        }
        compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsActions.SetText].action?.invoke(
            androidx.compose.ui.text.AnnotatedString("tea\n"),
        )
        assertEquals("tea", text)
        assertEquals(1, submitted)
    }
}
