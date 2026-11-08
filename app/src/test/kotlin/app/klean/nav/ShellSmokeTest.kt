package app.klean.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import app.klean.shared.module.ModuleId
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.theme.KleanTheme
import app.klean.shared.capture.DefaultCaptureRouter
import kotlin.time.Clock
import app.klean.core.ui.module.KleanModule
import app.klean.feature.diet.DietKleanModule
import app.klean.feature.diet.DietEnablement
import org.junit.Assert.assertEquals
import app.klean.core.ui.module.ModuleRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stand-in for the on-device check: with no modules the shell shows Today + Settings,
 * and the settings skeleton navigates to the modules screen. */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun shell(modules: Set<KleanModule>, gate: FakeModuleGate = FakeModuleGate()): ShellViewModel =
        ShellViewModel(ModuleRegistry(modules, gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System)

    // The real dashboard card needs Hilt; the shell only needs the module's metadata and routes.
    private fun diet(): KleanModule = object : KleanModule by DietKleanModule(DietEnablement { }) {
        @Composable override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
    }

    private fun openModulesScreen() {
        compose.onAllNodes(hasClickAction() and hasText("Settings"))[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Turn modules on or off").performClick()
        compose.waitForIdle()
    }

    /** Found on device: toggling a module rebuilt the nav graph and dropped the user back on Today. */
    @Test fun turningAModuleOffKeepsYouOnTheModulesScreen() {
        val vm = shell(setOf(diet()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        openModulesScreen()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertDoesNotExist()
    }

    /** Found on device: Settings listed a disabled module whose routes were gone, and tapping it crashed. */
    @Test fun disabledModuleDisappearsFromSettingsList() {
        val vm = shell(setOf(diet()), FakeModuleGate(setOf(ModuleId.DIET)))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction() and hasText("Settings"))[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onAllNodesWithText("Diet").assertCountEquals(0)
    }

    @Test fun shellShowsTodayAndSettingsWithEmptyState() {
        val vm = shell(emptySet())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Settings").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onNodeWithText("Turn modules on or off").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No modules are installed yet.").assertIsDisplayed()
    }

    @Test fun dietModuleAddsBottomBarItemBetweenTodayAndSettings() {
        val vm = shell(setOf(diet()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        val labels = compose.onAllNodes(hasClickAction() and (hasText("Today") or hasText("Diet") or hasText("Settings")))
            .fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
        assertEquals(listOf("Today", "Diet", "Settings"), labels)
    }
}
