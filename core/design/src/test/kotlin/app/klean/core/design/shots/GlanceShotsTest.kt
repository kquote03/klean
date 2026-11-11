package app.klean.core.design.shots

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.GlanceSection
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.QuickActionGrid
import app.klean.core.design.component.QuickActionUi
import app.klean.core.design.component.TimelineRow
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today's whiteboard pieces in light and dark, and at font scale 1.5 (src/test/snapshots/glance-*.png). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlanceShotsTest {
    @get:Rule val compose = createComposeRule()

    private val tapped = mutableListOf<String>()
    private fun tile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) = QuickActionUi(label, icon, primary = label == "Speak") { tapped += label }
    private val eight = listOf(
        tile("Speak", Icons.Rounded.Mic), tile("Type", Icons.Rounded.Keyboard), tile("Food", Icons.Rounded.Restaurant),
        tile("Task", Icons.Rounded.CheckCircleOutline), tile("Bedtime", Icons.Rounded.Bedtime), tile("Workout", Icons.Rounded.FitnessCenter),
        tile("Event", Icons.Rounded.EventAvailable), tile("Reminder", Icons.Rounded.NotificationsNone),
    )

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, scale), content = content)
    }

    @Test fun gridOfEight() {
        compose.shot("glance-grid") { QuickActionGrid(eight) }
        compose.onNodeWithText("Food").assertHeightIsAtLeast(72.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(listOf("Food"), tapped)
        // The grid is one region for the clutter budget, however many tiles it holds.
        compose.assertClutterBudget(maxInteractive = 1)
    }

    @Test fun gridAtLargeFont() {
        compose.shot("glance-grid-font150") { Scaled(1.5f) { QuickActionGrid(eight) } }
        compose.onNodeWithText("Bedtime").assertExists()
    }

    @Test fun shortGridKeepsTileWidths() {
        compose.shot("glance-grid-short") { QuickActionGrid(eight.take(5)) }
    }

    /** On the theme's surface, as on Today, so headers take the surface's content colour. */
    @Composable
    private fun Sections() = Surface { SectionsBody() }

    @Composable
    private fun SectionsBody() = Column {
        GlanceSection("Needs attention", first = true, content = {
            TimelineRow("12:20", "Lunch with Lina", detail = "Starts in 20 min", icon = Icons.Rounded.EventAvailable, onClick = {})
            TimelineRow(null, "3 from earlier days", icon = Icons.Rounded.CheckCircleOutline, onClick = {})
        })
        GlanceSection("Coming up", more = MenuAction("2 more") {}, content = {
            TimelineRow("3:00 PM", "Call the bank about the card that never arrived in the post", icon = Icons.Rounded.CheckCircleOutline, checkable = true, onClick = {})
            TimelineRow("5:00 PM", "Check-in around 5:00 PM", icon = Icons.Rounded.Restaurant)
        })
        GlanceSection("So far today", content = {
            TimelineRow("7:00 AM", "Slept 7 h 20 m", detail = "11:40 PM – 7:00 AM", icon = Icons.Rounded.Bedtime, dimmed = true, onClick = {})
            TimelineRow("8:10 AM", "coffee with milk", icon = Icons.Rounded.Restaurant, dimmed = true, onClick = {})
            TimelineRow("1:15 PM", "shawarma", detail = "chicken, garlic sauce", icon = Icons.Rounded.Restaurant, dimmed = true, onClick = {})
        })
    }

    @Test fun sectionsWithRows() {
        compose.shot("glance-sections") { Sections() }
        compose.onNodeWithText("shawarma").assertExists()
        compose.onNodeWithContentDescription("Done: Call the bank about the card that never arrived in the post").assertExists()
        compose.onNodeWithText("2 more").assertExists()
    }

    @Test fun sectionsAtLargeFont() {
        compose.shot("glance-sections-font150") { Scaled(1.5f) { Sections() } }
        compose.onNodeWithText("coffee with milk").assertExists()
    }

    @Test fun emptySectionIsOneCalmLine() {
        compose.shot("glance-empty") {
            Surface { Column {
                GlanceSection("Coming up", empty = "Nothing else today", first = true, content = null)
                GlanceSection("So far today", empty = "Nothing yet", content = null)
                // No empty line asked for: the section isn't drawn at all.
                GlanceSection("Needs attention", content = null)
            } }
        }
        compose.onNodeWithText("Nothing yet").assertExists()
        compose.onNodeWithText("Needs attention").assertDoesNotExist()
    }
}
