package app.klean.today

import app.klean.core.capture.UniversalCaptureService
import app.klean.nav.FakeModuleGate
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gate = FakeModuleGate()
    private val executed = mutableListOf<CaptureInput>()
    private val diet = object : CaptureHandler {
        override val moduleId = ModuleId.DIET
        override val actionSpecs = listOf(ActionSpec("log_food", "food"))
        override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, "log_food", 1f)
        override suspend fun execute(input: CaptureInput, action: CandidateAction): String { executed += input; return "id" }
        override suspend fun undo(recordId: String) = Unit
    }
    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(7) }
    private fun vm() = TodayViewModel(UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)), clock)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun submitSavesFromDashboardAndFlashesSaved() = runTest(dispatcher) {
        val vm = vm()
        var done: Boolean? = null
        vm.submit(" toast ") { done = it }
        runCurrent()
        assertEquals(CaptureSource.DASHBOARD, executed.single().source)
        assertEquals("toast", executed.single().text)
        assertEquals(true, done)
        assertTrue(vm.justSaved.value)
        advanceTimeBy(TodayViewModel.SAVED_MS + 1)
        assertFalse(vm.justSaved.value)
    }

    @Test fun blankIsIgnored() = runTest(dispatcher) {
        var done: Boolean? = null
        vm().submit("  ") { done = it }
        runCurrent()
        assertTrue(executed.isEmpty())
        assertEquals(false, done)
    }

    @Test fun captureUnavailableWhenAllModulesOff() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.captureAvailable.collect {} }
        runCurrent()
        assertTrue(vm.captureAvailable.value)
        gate.state.value = setOf(ModuleId.DIET)
        runCurrent()
        assertFalse(vm.captureAvailable.first())
        var done: Boolean? = null
        vm.submit("tea") { done = it }
        runCurrent()
        assertEquals(false, done)
        assertTrue(executed.isEmpty())
    }

    @Test fun aGuessCanBeMovedToAnyOtherModule() = runTest(dispatcher) {
        val undone = mutableListOf<String>()
        val tasksDone = mutableListOf<CaptureInput>()
        val dietUndo = object : CaptureHandler by diet {
            override suspend fun undo(recordId: String) { undone += recordId }
        }
        val tasks = object : CaptureHandler {
            override val moduleId = ModuleId.TASKS
            override val actionSpecs = listOf(ActionSpec("add_task", "task"))
            override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, "add_task", 1f)
            override suspend fun execute(input: CaptureInput, action: CandidateAction): String { tasksDone += input; return "t1" }
            override suspend fun undo(recordId: String) = Unit
        }
        val vm = TodayViewModel(UniversalCaptureService(setOf(dietUndo, tasks), gate, DefaultCaptureRouter(null)), clock)
        vm.submit("oats")
        runCurrent()
        val saved = vm.saved.value!!
        assertEquals(ModuleId.DIET, saved.module)
        assertEquals(listOf(ModuleId.TASKS), saved.options.map { it.moduleId })
        // The line with "Move to…" stays longer than a plain "Saved".
        advanceTimeBy(TodayViewModel.SAVED_MS + 1)
        assertTrue(vm.justSaved.value)
        vm.moveTo(saved.options.single())
        runCurrent()
        assertEquals("oats", tasksDone.single().text)
        assertEquals(listOf("id"), undone)
        assertEquals(TodaySaved(ModuleId.TASKS, emptyList()), vm.saved.value)
        advanceTimeBy(TodayViewModel.SAVED_MS + 1)
        assertEquals(null, vm.saved.value)
    }
}
