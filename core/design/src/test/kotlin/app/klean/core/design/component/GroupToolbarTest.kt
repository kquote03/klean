package app.klean.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import app.klean.core.design.theme.Space
import app.klean.core.testing.PHONE
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A group's tabs sit in a floating toolbar at the bottom, by the thumb, with the Add button at the end of the same row
 * (user feedback 2026-11-11). The list's last row clears both.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class GroupToolbarTest {
    @get:Rule val compose = createComposeRule()

    private var pad = PaddingValues()

    private fun render(leftHanded: Boolean = false, add: Boolean = true) {
        compose.setContent {
            KleanTheme {
                CompositionLocalProvider(
                    LocalGroupChrome provides GroupChromeResolver { GroupChrome("Track", listOf("Week", "Workout", "Sleep", "Diet"), 2, {}) },
                    LocalAddMenu provides AddMenuSpec(listOf(AddMenuItem("task", "Task", Icons.Rounded.CheckCircle) {}), onCapture = {}).takeIf { add },
                    LocalLeftHanded provides leftHanded,
                ) {
                    KleanScreen(title = "Sleep") { p ->
                        pad = p
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = p) { item { Text("Content") } }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(label: String): DpRect = compose.onNode(hasText(label) and hasClickAction()).getUnclippedBoundsInRoot()
    private fun add(): DpRect = compose.onNodeWithContentDescription("Add").getUnclippedBoundsInRoot()
    private fun DpRect.centerY() = (top + bottom) / 2

    @Test fun tabsSitAtTheBottomInOneRowWithAdd() {
        render()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val week = bounds("Week")
        val diet = bounds("Diet")
        val add = add()
        assertTrue("tabs in the lower part of the screen: ${week.top} of ${screen.bottom}", week.top > screen.bottom * 0.75f)
        assertTrue("Add after the last tab: ${diet.right} vs ${add.left}", diet.right <= add.left)
        assertTrue("one row: ${week.centerY()} vs ${add.centerY()}", (week.centerY() - add.centerY()).value in -4f..4f)
        assertTrue("tabs keep the screen edge: ${week.left}", week.left >= Space.screenEdge)
    }

    @Test fun theListClearsTheToolbar() {
        render()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val top = minOf(bounds("Week").top, add().top)
        assertTrue("bottom padding ${pad.calculateBottomPadding()} under the toolbar's ${screen.bottom - top}", pad.calculateBottomPadding() >= screen.bottom - top + Space.md - Space.xxs)
    }

    @Test fun leftHandedPutsAddFirst() {
        render(leftHanded = true)
        val add = add()
        val week = bounds("Week")
        assertTrue("Add before the first tab: ${add.right} vs ${week.left}", add.right <= week.left)
        assertTrue("one row", (week.centerY() - add.centerY()).value in -4f..4f)
    }

    @Test fun withoutAddTheTabsFillTheRow() {
        render(add = false)
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val diet = bounds("Diet")
        assertTrue("tabs at the bottom", diet.top > screen.bottom * 0.75f)
        assertTrue("to the screen edge: ${diet.right}", diet.right >= screen.right - Space.screenEdge - Space.md)
    }
}
