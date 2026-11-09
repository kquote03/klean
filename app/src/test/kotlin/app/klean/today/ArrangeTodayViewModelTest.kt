package app.klean.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.nav.FakeModuleGate
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
class ArrangeTodayViewModelTest {
    private class CardModule(override val id: ModuleId, override val dashboardSlot: DashboardSlot) : KleanModule {
        override val labelRes = android.R.string.ok
        override val icon get() = Icons.Rounded.Star
        override val navOrder = 1
        override val startRoute: Any = id.value
        override val settingsRoute: Any? = null
        override fun registerGraph(builder: NavGraphBuilder, nav: NavController) {}
    }

    private val gate = FakeModuleGate()
    private val registry = ModuleRegistry(
        setOf(
            CardModule(ModuleId.DIET, DashboardSlot.DIET),
            CardModule(ModuleId.TASKS, DashboardSlot.TASKS),
            CardModule(ModuleId.SLEEP, DashboardSlot.SLEEP),
        ),
        gate,
    )
    private val layout = InMemoryTodayLayout()
    private val store = ViewModelStore()

    private fun vm() = ArrangeTodayViewModel(registry, layout).also { store.put("vm", it) }
    private fun ArrangeTodayViewModel.ids() = rows.value.map { it.module.id.value }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun startsInSlotOrderWithEverythingShown() = runTest {
        val vm = vm()
        assertEquals(listOf("tasks", "sleep", "diet"), vm.ids())
        assertEquals(listOf(true, true, true), vm.rows.value.map { it.visible })
    }

    @Test fun moveUpAndDownPersistTheOrder() = runTest {
        val vm = vm()
        vm.moveUp("diet")
        assertEquals(listOf("tasks", "diet", "sleep"), layout.order.value)
        assertEquals(listOf("tasks", "diet", "sleep"), vm.ids())
        vm.moveDown("tasks")
        assertEquals(listOf("diet", "tasks", "sleep"), layout.order.value)
        // The ends stay put.
        vm.moveUp("diet")
        vm.moveDown("sleep")
        assertEquals(listOf("diet", "tasks", "sleep"), layout.order.value)
    }

    @Test fun hidingKeepsTheRowListedButSwitchedOff() = runTest {
        val vm = vm()
        vm.setVisible("diet", false)
        assertEquals(setOf("diet"), layout.hidden.value)
        assertEquals(listOf("tasks", "sleep", "diet"), vm.ids())
        assertEquals(false, vm.rows.value.last().visible)
        vm.setVisible("diet", true)
        assertEquals(emptySet<String>(), layout.hidden.value)
    }

    @Test fun aSwitchedOffModuleKeepsItsPlaceInTheSavedOrder() = runTest {
        layout.setOrder(listOf("diet", "sleep", "tasks"))
        gate.setEnabled(ModuleId.SLEEP, false)
        val vm = vm()
        vm.moveDown("diet")
        assertEquals(listOf("tasks", "diet", "sleep"), layout.order.value)
    }
}
