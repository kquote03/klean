package app.klean.core.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import app.klean.core.design.theme.KleanTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.unit.dp

/** Robolectric qualifiers for a Pixel-class phone, the prototypes' 412 × 915 frame. */
const val PHONE = "w412dp-h915dp-xxhdpi"

private const val SHOT = "shot"
private const val SETTLE_MS = 2_000L

/**
 * Renders [content] in light, then dark, and captures each into `src/test/snapshots/<name>-{light,dark}.png`
 * (`recordRoborazziDebug` writes them, `verifyRoborazziDebug` compares). A [screen] fills the phone frame;
 * a component sits on the surface with the screen edge around it, at phone width.
 *
 * Returns with the dark variant still composed, so callers can assert on it, change it and [Shot.capture] again.
 */
fun ComposeContentTestRule.shot(name: String, screen: Boolean = false, content: @Composable () -> Unit): Shot {
    val dark = mutableStateOf(false)
    setContent {
        KleanTheme(darkTheme = dark.value) {
            val frame = if (screen) Modifier.fillMaxSize() else Modifier.width(412.dp)
            Box(frame.testTag(SHOT).background(MaterialTheme.colorScheme.surface).padding(if (screen) 0.dp else 16.dp)) {
                content()
            }
        }
    }
    return Shot(this, dark).also { it.capture(name) }
}

/** The content [shot] composed; [capture] takes it again after the test changed it (a menu opened). */
class Shot internal constructor(private val rule: ComposeContentTestRule, private val dark: MutableState<Boolean>) {
    fun capture(name: String) {
        for (d in listOf(false, true)) {
            dark.value = d
            // Let ripples and menu animations settle, so a capture after a tap is the same on every run.
            rule.mainClock.advanceTimeBy(SETTLE_MS)
            rule.waitForIdle()
            rule.onNodeWithTag(SHOT).captureRoboImage(
                "src/test/snapshots/$name-${if (d) "dark" else "light"}.png",
                // A pressed button's ripple can leave a few pixels behind after a tap; layout changes are far larger.
                roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f)),
            )
        }
    }
}
