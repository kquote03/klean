package app.klean.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import app.klean.core.ui.module.InMemoryModuleGate
import app.klean.core.ui.module.ModuleRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Stand-in for the on-device check: with no modules the shell shows Today + Settings. */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shellShowsTodayAndSettingsWithEmptyState() {
        val vm = ShellViewModel(ModuleRegistry(emptySet(), InMemoryModuleGate()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Settings").fetchSemanticsNodes().size >= 2)
    }
}
