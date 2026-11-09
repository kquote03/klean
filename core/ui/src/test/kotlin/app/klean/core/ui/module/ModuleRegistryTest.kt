package app.klean.core.ui.module

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRegistryTest {

    private class FakeGate(initial: Set<ModuleId> = emptySet()) : ModuleGate {
        val state = MutableStateFlow(initial)
        override val disabledIds: Flow<Set<ModuleId>> = state
        override suspend fun isEnabled(id: ModuleId) = id !in state.value
        override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
            state.value = if (enabled) state.value - id else state.value + id
        }
        val seeded = mutableListOf<Set<ModuleId>>()
        override suspend fun seedNew(registered: Set<ModuleId>) { seeded += registered }
    }

    private class FakeModule(
        override val id: ModuleId,
        override val navOrder: Int,
        override val dashboardSlot: DashboardSlot?,
    ) : KleanModule {
        val enabledCalls = mutableListOf<Boolean>()
        override val labelRes: Int = 0
        override val icon: ImageVector get() = Icons.Rounded.Star
        override val startRoute: Any = id.value
        override val settingsRoute: Any? = null
        override fun registerGraph(builder: NavGraphBuilder, nav: NavController) {}
        override suspend fun onEnabledChanged(enabled: Boolean) { enabledCalls += enabled }
    }

    private val diet = FakeModule(ModuleId.DIET, navOrder = 3, dashboardSlot = DashboardSlot.DIET)
    private val tasks = FakeModule(ModuleId.TASKS, navOrder = 1, dashboardSlot = DashboardSlot.TASKS)
    private val sleep = FakeModule(ModuleId.SLEEP, navOrder = 2, dashboardSlot = DashboardSlot.SLEEP)
    private val contacts = FakeModule(ModuleId.CONTACTS, navOrder = 0, dashboardSlot = null)

    @Test fun enabledExcludesDisabledIds() = runTest {
        val registry = ModuleRegistry(setOf(diet, tasks, sleep), FakeGate(setOf(ModuleId.TASKS)))
        assertEquals(listOf(ModuleId.SLEEP, ModuleId.DIET), registry.enabled.first().map { it.id })
        assertEquals(listOf(ModuleId.TASKS, ModuleId.SLEEP, ModuleId.DIET), registry.all.map { it.id })
    }

    @Test fun dashboardCardsOrderedBySlotNotNavOrder() = runTest {
        val first = FakeModule(ModuleId.DIET, navOrder = 0, dashboardSlot = DashboardSlot.DIET)
        val registry = ModuleRegistry(setOf(sleep, tasks, first), FakeGate())
        // navOrder: diet(0), tasks(1), sleep(2). Slot order (H4): TASKS, SLEEP, DIET last.
        assertEquals(listOf(ModuleId.TASKS, ModuleId.SLEEP, ModuleId.DIET), registry.dashboardCards.first().map { it.id })
    }

    @Test fun setEnabledNotifiesOnlyThatModule() = runTest {
        val gate = FakeGate()
        val registry = ModuleRegistry(setOf(diet, tasks), gate)
        registry.setEnabled(ModuleId.DIET, false)
        assertEquals(listOf(false), diet.enabledCalls)
        assertTrue(tasks.enabledCalls.isEmpty())
        assertEquals(setOf(ModuleId.DIET), gate.state.value)
        assertEquals(listOf(ModuleId.TASKS), registry.enabled.first().map { it.id })
    }

    @Test fun moduleWithoutSlotHasNoCard() = runTest {
        val registry = ModuleRegistry(setOf(contacts, diet), FakeGate())
        assertEquals(listOf(ModuleId.DIET), registry.dashboardCards.first().map { it.id })
        assertEquals(listOf(ModuleId.CONTACTS, ModuleId.DIET), registry.enabled.first().map { it.id })
    }

    @Test fun seedOffersEveryRegisteredModuleToTheGate() = runTest {
        val gate = FakeGate()
        ModuleRegistry(setOf(diet, tasks, sleep), gate).seed()
        assertEquals(listOf(setOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP)), gate.seeded)
    }
}
