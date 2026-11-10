package app.klean.core.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import app.klean.core.design.theme.KleanTheme
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp

/** Robolectric qualifiers for a Pixel-class phone, the prototypes' 412 × 915 frame. */
const val PHONE = "w412dp-h915dp-xxhdpi"

private const val SHOT = "shot"

/**
 * Renders [content] in light, then dark, and captures each into `src/test/snapshots/<name>-{light,dark}.png`
 * (`recordRoborazziDebug` writes them, `verifyRoborazziDebug` compares). A [screen] fills the phone frame;
 * a component sits on the surface with the screen edge around it, at phone width.
 *
 * Returns with the dark variant still composed, so callers can assert on it.
 */
fun ComposeContentTestRule.shot(name: String, screen: Boolean = false, content: @Composable () -> Unit) {
    var dark by mutableStateOf(false)
    setContent {
        KleanTheme(darkTheme = dark) {
            val frame = if (screen) Modifier.fillMaxSize() else Modifier.width(412.dp)
            Box(frame.testTag(SHOT).background(MaterialTheme.colorScheme.surface).padding(if (screen) 0.dp else 16.dp)) {
                content()
            }
        }
    }
    for (d in listOf(false, true)) {
        dark = d
        waitForIdle()
        onNodeWithTag(SHOT).captureRoboImage("src/test/snapshots/$name-${if (d) "dark" else "light"}.png")
    }
}
