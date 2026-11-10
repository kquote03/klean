package app.klean.core.design.shots

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.KleanCard
import app.klean.core.design.component.SummaryTile
import app.klean.core.testing.PHONE
import app.klean.core.testing.shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every shared component in light and dark at phone width (reference images in src/test/snapshots). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentShotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryTile() = compose.shot("summary-tile") {
        SummaryTile("Last night", "7h 10m", Icons.Rounded.Today, onClick = {})
    }

    @Test fun kleanCard() = compose.shot("klean-card") {
        KleanCard(title = "Workout", onOpen = {}) { Text("Push day is saved") }
    }
}
