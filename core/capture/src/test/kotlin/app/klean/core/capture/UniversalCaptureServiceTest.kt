package app.klean.core.capture

import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureRouter
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalCaptureServiceTest {
    private val diet = FakeHandler(ModuleId.DIET, repeats = listOf("tea", "toast"))
    private val tasks = FakeHandler(ModuleId.TASKS, repeats = listOf("call mum", "tea"))
    private val gate = FakeModuleGate()
    private fun input(text: String = "toast") = CaptureInput(text, CaptureSource.DASHBOARD, 0L)

    @Test fun savesToTheDefaultModule() = runTest {
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        val r = s.submit(input()) as CaptureResult.Saved
        assertEquals(ModuleId.DIET, r.moduleId)
        assertEquals("diet-1", r.recordId)
        assertEquals(1, diet.executed.size)
    }

    @Test fun disabledModulesHandlerIsNeverUsed() = runTest {
        gate.state.value = setOf(ModuleId.DIET)
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        val r = s.submit(input()) as CaptureResult.Saved
        assertEquals(ModuleId.TASKS, r.moduleId)
        assertTrue(diet.executed.isEmpty())
    }

    @Test fun noModuleWhenAllAreDisabled() = runTest {
        gate.state.value = setOf(ModuleId.DIET, ModuleId.TASKS)
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        assertEquals(CaptureResult.NoModule, s.submit(input()))
        assertTrue(diet.executed.isEmpty() && tasks.executed.isEmpty())
    }

    @Test fun correctSavesTheNewRecordThenUndoesTheOld() = runTest {
        val ranked = object : CaptureRouter {
            override suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>) =
                listOf(CandidateAction(ModuleId.DIET, "add", 0.6f), CandidateAction(ModuleId.TASKS, "add", 0.4f))
        }
        val s = UniversalCaptureService(setOf(diet, tasks), gate, ranked)
        val saved = s.submit(input()) as CaptureResult.Saved
        assertEquals(listOf(ModuleId.TASKS), saved.alternatives.map { it.moduleId })
        val moved = s.correct(saved, saved.alternatives.single())
        assertEquals(ModuleId.TASKS, moved.moduleId)
        assertEquals("tasks-1", moved.recordId)
        assertEquals(listOf("diet-1"), diet.undone)
        assertEquals(saved.input, tasks.executed.single())
        // Moving back is offered.
        assertEquals(listOf(ModuleId.DIET), moved.alternatives.map { it.moduleId })
    }

    @Test fun correctToDisabledModuleKeepsTheOriginal() = runTest {
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        val saved = s.submit(input()) as CaptureResult.Saved
        gate.state.value = setOf(ModuleId.TASKS)
        assertEquals(saved, s.correct(saved, CandidateAction(ModuleId.TASKS, "add", 1f)))
        assertTrue(diet.undone.isEmpty())
    }

    @Test fun suggestionsComeFromEnabledHandlersOnly() = runTest {
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        assertEquals(setOf("tea", "toast", "call mum"), s.suggestions().toSet())
        assertEquals(listOf("tea", "toast"), s.suggestions(ModuleId.DIET))
        gate.state.value = setOf(ModuleId.DIET)
        assertEquals(listOf("call mum", "tea"), s.suggestions())
    }

    @Test fun availableHandlersFollowTheGate() = runTest {
        val s = UniversalCaptureService(setOf(diet, tasks), gate, DefaultCaptureRouter(null))
        assertEquals(2, s.availableHandlers.first().size)
        gate.state.value = setOf(ModuleId.DIET)
        assertEquals(listOf(ModuleId.TASKS), s.availableHandlers.first().map { it.moduleId })
    }
}
