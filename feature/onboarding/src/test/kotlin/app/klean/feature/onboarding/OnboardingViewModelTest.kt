package app.klean.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.onboarding.OnboardingStep.Ai
import app.klean.feature.onboarding.OnboardingStep.ModuleSetup
import app.klean.feature.onboarding.OnboardingStep.Modules
import app.klean.feature.onboarding.OnboardingStep.Permissions
import app.klean.feature.onboarding.OnboardingStep.SleepWindow
import app.klean.feature.onboarding.OnboardingStep.Welcome
import app.klean.feature.onboarding.OnboardingStep.Widget
import app.klean.feature.settings.quiet.QuietHoursStore
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeOnboardingState(var show: Boolean = true) : OnboardingState {
    var done = 0
    override suspend fun shouldShowWizard() = show
    override suspend fun markDone() { done++; show = false }
}

class FakeQuietHoursStore(initial: QuietHours = QuietHours.DEFAULT) : QuietHoursStore {
    val state = MutableStateFlow(initial)
    val writes = mutableListOf<QuietHours>()
    override val quietHours: Flow<QuietHours> = state
    override suspend fun set(value: QuietHours) { writes += value; state.value = value }
}

class FakeModuleGate : ModuleGate {
    val state = MutableStateFlow(emptySet<ModuleId>())
    override val disabledIds: Flow<Set<ModuleId>> = state
    override suspend fun isEnabled(id: ModuleId) = id !in state.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        state.value = if (enabled) state.value - id else state.value + id
    }
}

class FakeModule(
    override val id: ModuleId,
    override val onboardingTitleRes: Int? = 1,
    override val widgetReceiver: Class<*>? = null,
) : KleanModule {
    override val labelRes = 1
    override val icon: ImageVector get() = Icons.Rounded.Star
    override val navOrder = 1
    override val dashboardSlot: DashboardSlot? = null
    override val startRoute: Any = id.value
    override val settingsRoute: Any? = null
    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val gate = FakeModuleGate()
    private val state = FakeOnboardingState()
    private val quiet = FakeQuietHoursStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg modules: KleanModule) = OnboardingViewModel(ModuleRegistry(modules.toSet(), gate), quiet, state)

    @Test fun stepsInOrderWithAModuleStepAndWidget() {
        val vm = vm(FakeModule(ModuleId.DIET, widgetReceiver = Any::class.java))
        assertEquals(
            listOf(Welcome, Modules, SleepWindow, ModuleSetup(ModuleId.DIET), Widget, Permissions, Ai),
            vm.state.value.steps,
        )
        assertEquals(Welcome, vm.state.value.current)
    }

    @Test fun noModulesMeansNoModuleWidgetOrModulesSteps() {
        assertEquals(listOf(Welcome, SleepWindow, Permissions, Ai), vm().state.value.steps)
    }

    @Test fun turningAModuleOffInTheModulesStepDropsItsSetupStep() = runTest {
        val vm = vm(FakeModule(ModuleId.DIET, widgetReceiver = Any::class.java))
        vm.next() // → Modules
        gate.setEnabled(ModuleId.DIET, false)
        assertEquals(listOf(Welcome, Modules, SleepWindow, Permissions, Ai), vm.state.value.steps)
        vm.next()
        vm.next()
        assertEquals(Permissions, vm.state.value.current)
    }

    @Test fun nextOnSleepWindowSavesTheChosenHours() {
        val vm = vm()
        vm.next()
        assertEquals(SleepWindow, vm.state.value.current)
        assertEquals(QuietHours.DEFAULT, vm.state.value.sleep)
        vm.setSleepStart(23 * 60)
        vm.setSleepEnd(7 * 60 + 30)
        assertTrue(quiet.writes.isEmpty()) // nothing is written until the step is accepted
        vm.next()
        assertEquals(listOf(QuietHours(23 * 60, 7 * 60 + 30)), quiet.writes)
        assertEquals(Permissions, vm.state.value.current)
    }

    @Test fun skipOnSleepWindowLeavesTheSettingAlone() {
        val vm = vm()
        vm.next()
        vm.setSleepStart(22 * 60)
        vm.skip()
        assertTrue(quiet.writes.isEmpty())
        assertEquals(Permissions, vm.state.value.current)
    }

    @Test fun sleepStepStartsFromTheSavedHours() {
        quiet.state.value = QuietHours(60, 9 * 60)
        val vm = vm()
        assertEquals(QuietHours(60, 9 * 60), vm.state.value.sleep)
    }

    @Test fun skippingEveryStepFinishesAndMarksDone() {
        val vm = vm(FakeModule(ModuleId.DIET, widgetReceiver = Any::class.java))
        repeat(vm.state.value.steps.size) {
            assertFalse(vm.state.value.finished)
            vm.skip()
        }
        assertTrue(vm.state.value.finished)
        assertEquals(1, state.done)
        assertTrue(quiet.writes.isEmpty())
    }

    @Test fun skipSetupFromWelcomeFinishesAtOnce() {
        val vm = vm(FakeModule(ModuleId.DIET))
        vm.skipAll()
        assertTrue(vm.state.value.finished)
        assertEquals(1, state.done)
    }

    @Test fun backGoesToThePreviousStepAndStopsAtWelcome() {
        val vm = vm()
        vm.back()
        assertEquals(Welcome, vm.state.value.current)
        vm.next()
        vm.next()
        assertEquals(Permissions, vm.state.value.current)
        vm.back()
        assertEquals(SleepWindow, vm.state.value.current)
    }

    @Test fun lastStepIsMarked() {
        val vm = vm()
        repeat(3) { vm.next() }
        assertEquals(Ai, vm.state.value.current)
        assertTrue(vm.state.value.isLast)
        assertEquals(4, vm.state.value.position)
    }
}
