package app.klean.core.capture

import androidx.lifecycle.SavedStateHandle
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val diet = FakeHandler(ModuleId.DIET, repeats = listOf("tea"))
    private val gate = FakeModuleGate()
    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(42) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg extras: Pair<String, Any?>) = CaptureViewModel(
        UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)),
        ModuleRegistry(emptySet(), gate),
        clock,
        SavedStateHandle(mapOf(*extras)),
    )

    @Test fun savesWithSourceFromExtrasThenCloses() = runTest(dispatcher) {
        val vm = vm(CaptureActivity.EXTRA_SOURCE to CaptureSource.QS_TILE.name, CaptureActivity.EXTRA_TARGET_MODULE to "diet")
        runCurrent()
        assertEquals(listOf("tea"), vm.state.value.suggestions)
        vm.submit("  toast ")
        runCurrent()
        val input = diet.executed.single()
        assertEquals("toast", input.text)
        assertEquals(CaptureSource.QS_TILE, input.source)
        assertEquals(ModuleId.DIET, input.targetModule)
        assertEquals(42L, input.capturedAt)
        assertTrue(vm.state.value.phase is CapturePhase.Saved)
        assertFalse(vm.state.value.finished)
        advanceTimeBy(CaptureViewModel.CONFIRM_MS + 1)
        assertTrue(vm.state.value.finished)
    }

    @Test fun blankTextAndDoubleSubmitAreIgnored() = runTest(dispatcher) {
        val vm = vm()
        vm.submit("   ")
        runCurrent()
        assertTrue(diet.executed.isEmpty())
        vm.submit("tea")
        vm.submit("tea")
        runCurrent()
        assertEquals(1, diet.executed.size)
        assertEquals(CaptureActivity.DEFAULT_SOURCE, diet.executed.single().source)
    }

    @Test fun noModuleStaysOpen() = runTest(dispatcher) {
        gate.state.value = setOf(ModuleId.DIET)
        val vm = vm()
        vm.submit("tea")
        advanceTimeBy(10_000)
        assertEquals(CapturePhase.NoModule, vm.state.value.phase)
        assertFalse(vm.state.value.finished)
    }
}
