package app.klean.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
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
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Today's whiteboard in light and dark: typical, empty, a long day, large text (src/test/snapshots/today-*.png). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayShotsTest {
    @get:Rule val compose = createComposeRule()

    private val add = AddMenuSpec(listOf(AddMenuItem("tasks", "Task", Icons.Rounded.CheckCircleOutline) {}), onCapture = {})

    private fun render(
        name: String,
        state: TodayState = TodayFixtures.state(),
        avatar: TodayAvatar = TodayAvatar(),
        onAccount: () -> Unit = {},
        fontScale: Float = 1f,
    ) = compose.shot(name, screen = true) {
        Scaled(fontScale) {
            CompositionLocalProvider(LocalAddMenu provides add) {
                TodayContent(
                    state = state,
                    canArrange = true,
                    onQuickAction = {},
                    onVoice = {},
                    onType = {},
                    onOpen = {},
                    onArrange = {},
                    onSettings = {},
                    avatar = avatar,
                    onAccount = onAccount,
                    timeOf = TodayFixtures.timeOf,
                )
            }
        }
    }

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, scale), content = content)
    }

    @Test fun typical() {
        render("today-typical")
        compose.onNodeWithText("Tuesday").assertExists()
        compose.onNodeWithContentDescription("Account and settings").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        render("today-empty", TodayFixtures.state(TodayFixtures.empty))
        compose.onNodeWithText("Nothing yet").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun noModules() {
        render("today-no-modules", TodayFixtures.state(EMPTY_GLANCE, noModules = true))
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun longDay() {
        val g = TodayFixtures.typical
        val foods = (0 until 12).map { i ->
            g.done.rows[1].copy(key = "f$i", text = "snack $i with a longer description that wraps", at = TodayFixtures.at(9 + i / 2, (i % 2) * 30))
        }
        val long = g.copy(
            attention = g.attention.copy(moreCount = 2),
            done = GlanceSection((g.done.rows + foods).sortedBy { it.at }),
        )
        render("today-long", TodayFixtures.state(long))
        compose.assertClutterBudget()
    }

    @Test fun largeText() {
        render("today-font150", fontScale = 1.5f)
        compose.onNodeWithText("Bedtime").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun avatarWithAPhotoOpensTheAccountMenu() {
        var opened = 0
        render("today-avatar-photo", avatar = TodayAvatar(photo = testPhoto(), initial = "A", canSwitch = true), onAccount = { opened++ })
        compose.onNodeWithContentDescription("Account and settings").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, opened)
        compose.assertClutterBudget()
    }

    @Test fun avatarWithoutAPhotoShowsTheInitial() {
        render("today-avatar-initial", avatar = TodayAvatar(initial = "A"))
        compose.onNodeWithText("A").assertExists()
        compose.assertClutterBudget()
    }

    /** A stand-in profile photo: two soft circles, so the crop and clip are visible in the reference. */
    private fun testPhoto(): ImageBitmap {
        val img = ImageBitmap(192, 192)
        val c = Canvas(img)
        c.drawRect(0f, 0f, 192f, 192f, Paint().apply { color = Color(0xFF9DB8A0) })
        c.drawCircle(Offset(96f, 76f), 40f, Paint().apply { color = Color(0xFFF2D7C9) })
        c.drawCircle(Offset(96f, 200f), 80f, Paint().apply { color = Color(0xFF6E7FA8) })
        return img
    }
}
