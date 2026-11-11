package app.klean.core.capture

import androidx.lifecycle.SavedStateHandle
import app.klean.core.capture.voice.FakeVoiceRecognizer
import app.klean.core.capture.voice.VoiceSession
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.common.ZoneProvider
import kotlinx.datetime.TimeZone
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

object DubaiZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.of("Asia/Dubai")
}

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val diet = FakeHandler(ModuleId.DIET, repeats = listOf("tea"))
    private val gate = FakeModuleGate()
    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(42) }
    private val rec = FakeVoiceRecognizer()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg extras: Pair<String, Any?>) = CaptureViewModel(
        UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)),
        ModuleRegistry(emptySet(), gate),
        clock,
        rec,
        FakePhotoStorage(),
        DubaiZone,
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

    @Test fun universalSheetUsesTheHolisticPlaceholder() = runTest(dispatcher) {
        assertEquals(R.string.capture_placeholder, vm().state.value.placeholderRes)
    }

    /** The in-app sheet (plan 2026-11-11, D3) and CaptureActivity both aim the sheet through start(). */
    @Test fun startAimsTheSheetLikeTheActivityExtras() = runTest(dispatcher) {
        val module = PlaceholderModule(ModuleId.DIET, placeholder = 4242)
        val saved = SavedStateHandle()
        val vm = CaptureViewModel(
            UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)),
            ModuleRegistry(setOf(module), gate),
            clock, rec, FakePhotoStorage(), DubaiZone, saved,
        )
        runCurrent()
        assertEquals(R.string.capture_placeholder, vm.state.value.placeholderRes)
        vm.start(ModuleId.DIET, CaptureSource.DASHBOARD, voice = true, text = "oats")
        runCurrent()
        assertEquals(4242, vm.state.value.placeholderRes)
        assertEquals("oats", vm.state.value.text)
        assertEquals(listOf("tea"), vm.state.value.suggestions)
        assertTrue(vm.consumeStartVoice())
        // Kept for a recreation: a new view model over the same saved state is aimed the same way.
        assertEquals("diet", saved.get<String>(CaptureActivity.EXTRA_TARGET_MODULE))
        vm.submit(vm.state.value.text)
        runCurrent()
        assertEquals(ModuleId.DIET, diet.executed.single().targetModule)
        assertEquals(CaptureSource.DASHBOARD, diet.executed.single().source)
    }

    @Test fun moduleTargetedSheetKeepsTheModulePlaceholder() = runTest(dispatcher) {
        val module = PlaceholderModule(ModuleId.DIET, placeholder = 4242)
        val vm = CaptureViewModel(
            UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)),
            ModuleRegistry(setOf(module), gate),
            clock, rec, FakePhotoStorage(), DubaiZone,
            SavedStateHandle(mapOf(CaptureActivity.EXTRA_TARGET_MODULE to "diet")),
        )
        assertEquals(4242, vm.state.value.placeholderRes)
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

    @Test fun voiceFinalAutoSavesViaVoiceAndUndoReturnsTextToTheField() = runTest(dispatcher) {
        val vm = vm(CaptureActivity.EXTRA_TARGET_MODULE to "diet")
        runCurrent()
        vm.voice.onMic()
        rec.final("two manakish")
        runCurrent()
        val input = diet.executed.single()
        assertEquals("two manakish", input.text)
        assertTrue(input.viaVoice)
        assertTrue(vm.state.value.phase is CapturePhase.Saved)
        assertEquals("two manakish", vm.voice.state.value.undo?.text)
        // Stays open past the normal confirmation so the Undo chip can be used.
        advanceTimeBy(CaptureViewModel.CONFIRM_MS + 1)
        assertFalse(vm.state.value.finished)
        vm.voice.undoLast()
        runCurrent()
        assertEquals(listOf("diet-1"), diet.undone)
        assertEquals(CapturePhase.Editing, vm.state.value.phase)
        assertEquals("two manakish", vm.state.value.text)
        advanceTimeBy(10_000)
        assertFalse(vm.state.value.finished)
    }

    @Test fun voiceSaveClosesAfterTheUndoWindow() = runTest(dispatcher) {
        val vm = vm()
        vm.voice.onMic()
        rec.final("tea")
        runCurrent()
        advanceTimeBy(VoiceSession.UNDO_MS + 1)
        assertTrue(vm.state.value.finished)
        assertTrue(diet.undone.isEmpty())
    }

    @Test fun startVoiceExtraIsConsumedOnce() = runTest(dispatcher) {
        val vm = vm(CaptureActivity.EXTRA_START_VOICE to true)
        assertTrue(vm.consumeStartVoice())
        assertFalse(vm.consumeStartVoice())
        assertFalse(vm(CaptureActivity.EXTRA_START_VOICE to false).consumeStartVoice())
    }

    @Test fun typedTextLivesInState() = runTest(dispatcher) {
        val vm = vm()
        vm.onTextChange("toa")
        assertEquals("toa", vm.state.value.text)
        vm.submit(vm.state.value.text)
        runCurrent()
        assertFalse(diet.executed.single().viaVoice)
    }

    @Test fun gapPresetTravelsWithTheSaveAndTheTimeCanChange() = runTest(dispatcher) {
        // 2026-11-09 14:05 in Dubai (UTC+4) = 10:05 UTC.
        val preset = 1_794_218_700_000L
        val vm = vm(CaptureActivity.EXTRA_EATEN_AT to preset, CaptureActivity.EXTRA_TARGET_MODULE to "diet")
        assertEquals(preset, vm.state.value.eatenAt)
        assertEquals(14, vm.state.value.eatenHour)
        assertEquals(5, vm.state.value.eatenMinute)
        vm.onEatenTimePicked(13, 30)
        assertEquals(preset - 35 * 60_000L, vm.state.value.eatenAt)
        vm.submit("falafel")
        runCurrent()
        assertEquals(preset - 35 * 60_000L, diet.executed.single().eatenAt)
    }

    @Test fun withoutPresetTheEntryIsForNow() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(null, vm.state.value.eatenAt)
        vm.submit("tea")
        runCurrent()
        assertEquals(null, diet.executed.single().eatenAt)
    }

    @Test fun noModuleStaysOpen() = runTest(dispatcher) {
        gate.state.value = setOf(ModuleId.DIET)
        val vm = vm()
        vm.submit("tea")
        advanceTimeBy(10_000)
        assertEquals(CapturePhase.NoModule, vm.state.value.phase)
        assertFalse(vm.state.value.finished)
    }

    @Test fun aGuessOffersMoveToEveryOtherModuleAndMovesIt() = runTest(dispatcher) {
        val tasks = FakeHandler(ModuleId.TASKS)
        val vm = CaptureViewModel(
            UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null)),
            ModuleRegistry(emptySet(), gate), clock, rec, FakePhotoStorage(), DubaiZone, SavedStateHandle(),
        )
        vm.submit("oats")
        runCurrent()
        val phase = vm.state.value.phase as CapturePhase.SavedWithAlternatives
        // Opening the menu keeps the sheet open past the usual wait.
        vm.holdOpen(true)
        advanceTimeBy(CaptureViewModel.ALTERNATIVES_MS + 1_000)
        assertFalse(vm.state.value.finished)
        assertEquals(emptyList<MoveOption>(), phase.options)
        assertEquals(listOf(ModuleId.TASKS), phase.more.map { it.action.moduleId })
        vm.moveTo(phase.more.single())
        runCurrent()
        assertEquals("oats", tasks.executed.single().text)
        assertEquals(listOf("diet-1"), diet.undone)
        assertTrue(vm.state.value.phase is CapturePhase.Saved)
    }

    @Test fun aPrefilledSheetShowsTheText() = runTest(dispatcher) {
        assertEquals("call the bank", vm(CaptureActivity.EXTRA_TEXT to "call the bank").state.value.text)
    }
}
