package app.klean.core.design.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.CalorieProgress
import app.klean.core.design.theme.Space
import app.klean.core.testing.PHONE
import app.klean.core.testing.shot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The calorie goal bar: empty, part way, past the goal (full, same colour), estimated, and at font scale 1.5. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalorieProgressShotsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun states() {
        compose.shot("calorie-progress") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                CalorieProgress(eaten = 0, goal = 2000, estimated = false)
                CalorieProgress(eaten = 1240, goal = 2000, estimated = false)
                CalorieProgress(eaten = 2240, goal = 2000, estimated = false)
                CalorieProgress(eaten = 555, goal = 2000, estimated = true)
            }
        }
        compose.onNodeWithText("1,240 of 2,000 kcal").assertExists()
        compose.onNodeWithText("2,240 of 2,000 kcal").assertExists()
        compose.onNodeWithText("≈ 555 of 2,000 kcal").assertExists()
    }

    @Test fun largeText() {
        compose.shot("calorie-progress-large") {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 1.5f)) {
                CalorieProgress(eaten = 1240, goal = 2000, estimated = true)
            }
        }
    }
}
