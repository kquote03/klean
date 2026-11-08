package app.klean.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import app.cash.turbine.test
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.InMemoryModuleGate
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModulesSettingsViewModelTest {
    private class FakeModule(override val id: ModuleId, override val navOrder: Int) : KleanModule {
        val calls = mutableListOf<Boolean>()
        override val labelRes = navOrder
        override val icon: ImageVector get() = Icons.Rounded.Star
        override val dashboardSlot: DashboardSlot? = null
        override val startRoute: Any = id.value
        override val settingsRoute: Any? = null
        override fun registerGraph(builder: NavGraphBuilder, nav: NavController) {}
        override suspend fun onEnabledChanged(enabled: Boolean) { calls += enabled }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun rowsReflectGateAndToggleCallsRegistry() = runTest {
        val diet = FakeModule(ModuleId.DIET, 1)
        val sleep = FakeModule(ModuleId.SLEEP, 2)
        val vm = ModulesSettingsViewModel(ModuleRegistry(setOf(sleep, diet), InMemoryModuleGate()))
        vm.rows.test {
            // The initial empty stateIn value may be conflated away, depending on dispatch timing.
            val first = awaitItem().ifEmpty { awaitItem() }
            assertEquals(
                listOf(ModuleToggleRow(ModuleId.DIET, 1, true), ModuleToggleRow(ModuleId.SLEEP, 2, true)),
                first,
            )
            vm.setEnabled(ModuleId.SLEEP, false)
            assertEquals(
                listOf(ModuleToggleRow(ModuleId.DIET, 1, true), ModuleToggleRow(ModuleId.SLEEP, 2, false)),
                awaitItem(),
            )
        }
        assertEquals(listOf(false), sleep.calls)
        assertEquals(emptyList<Boolean>(), diet.calls)
    }
}
