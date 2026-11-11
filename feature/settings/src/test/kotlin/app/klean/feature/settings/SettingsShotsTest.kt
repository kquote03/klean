package app.klean.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings hub and a sub-screen in light and dark: calm groups, one list, a back arrow. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsShotsTest {
    @get:Rule val compose = createComposeRule()

    /** A module labelled with a framework string (no feature resources here). */
    private class Module(override val id: ModuleId, override val labelRes: Int, override val icon: ImageVector, override val navOrder: Int) : KleanModule {
        override val dashboardSlot: DashboardSlot? = null
        override val startRoute: Any = id.value
        override val settingsRoute: Any = "settings/${id.value}"
        override fun registerGraph(builder: NavGraphBuilder, nav: NavController) {}
    }

    private val modules = listOf(
        Module(ModuleId.TASKS, android.R.string.ok, Icons.Rounded.CheckCircle, 1),
        Module(ModuleId.WORKOUT, android.R.string.cut, Icons.Rounded.FitnessCenter, 2),
        Module(ModuleId.SLEEP, android.R.string.copy, Icons.Rounded.Bedtime, 3),
    )

    @Test fun hub() {
        val opened = mutableListOf<Any>()
        var back = 0
        compose.shot("settings-hub", screen = true) {
            SettingsScreen(modules, onOpen = { opened += it }, onRunSetup = {}, onBack = { back++ })
        }
        compose.onNodeWithText("Modules").assertExists()
        compose.onNodeWithText("Klean").assertExists()
        compose.onNodeWithText("Cut").performClick()
        compose.onNodeWithText("Turn modules on or off").performClick()
        assertEquals(listOf<Any>("settings/workout", ModulesSettingsRoute), opened)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
        // The back arrow and one list.
        compose.assertClutterBudget()
    }

    @Test fun subScreen() {
        val registry = ModuleRegistry(modules.toSet(), FakeModuleGate(setOf(ModuleId.SLEEP)))
        compose.shot("settings-modules", screen = true) { ModulesSettingsScreen(registry = registry, onBack = {}) }
        compose.onNodeWithText("Turn modules on or off").assertExists()
        compose.onNodeWithContentDescription("Back").assertExists()
    }
}
