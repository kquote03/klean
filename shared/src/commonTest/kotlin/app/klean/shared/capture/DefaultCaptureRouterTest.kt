package app.klean.shared.capture

import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCaptureRouterTest {
    private class FakeHandler(override val moduleId: ModuleId) : CaptureHandler {
        override val actionSpecs = listOf(ActionSpec("log_${moduleId.value}", "Something for ${moduleId.value}"))
        override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, actionSpecs.first().type, 1f)
        override suspend fun execute(input: CaptureInput, action: CandidateAction) = "id"
        override suspend fun undo(recordId: String) {}
    }
    private class FakeClassifier(val result: suspend () -> List<CandidateAction>?) : CaptureClassifier {
        var calls = 0
        override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? { calls++; return result() }
    }

    private val diet = FakeHandler(ModuleId.DIET)
    private val tasks = FakeHandler(ModuleId.TASKS)
    private val input = CaptureInput("oats", CaptureSource.QS_TILE, 0L)
    private val dietDefault = diet.defaultAction(input)
    private val tasksDefault = tasks.defaultAction(input)

    @Test fun noHandlers_empty() = runTest {
        assertEquals(emptyList(), DefaultCaptureRouter(FakeClassifier { null }).route(input, emptyList()))
    }
    @Test fun singleHandler_classifierNeverCalled() = runTest {
        val c = FakeClassifier { listOf(CandidateAction(ModuleId.DIET, "x", 1f)) }
        assertEquals(listOf(dietDefault), DefaultCaptureRouter(c).route(input, listOf(diet)))
        assertEquals(0, c.calls)
    }
    @Test fun targetModule_skipsClassifier() = runTest {
        val c = FakeClassifier { listOf(CandidateAction(ModuleId.DIET, "x", 1f)) }
        val r = DefaultCaptureRouter(c).route(input.copy(targetModule = ModuleId.TASKS), listOf(diet, tasks))
        assertEquals(listOf(tasksDefault), r)
        assertEquals(0, c.calls)
    }
    @Test fun classifierNull_dietDefault() = runTest {
        assertEquals(listOf(dietDefault), DefaultCaptureRouter(FakeClassifier { null }).route(input, listOf(diet, tasks)))
    }
    @Test fun noClassifier_dietDefault() = runTest {
        assertEquals(listOf(dietDefault), DefaultCaptureRouter(null).route(input, listOf(tasks, diet)))
    }
    @Test fun confidentClassifier_sortedByConfidence() = runTest {
        val t = CandidateAction(ModuleId.TASKS, "log_tasks", 0.9f)
        val d = CandidateAction(ModuleId.DIET, "log_diet", 0.2f)
        assertEquals(listOf(t, d), DefaultCaptureRouter(FakeClassifier { listOf(d, t) }).route(input, listOf(diet, tasks)))
    }
    @Test fun lowConfidence_defaultFirstThenCorrectionChip() = runTest {
        val t = CandidateAction(ModuleId.TASKS, "log_tasks", 0.3f)
        assertEquals(listOf(dietDefault, t), DefaultCaptureRouter(FakeClassifier { listOf(t) }).route(input, listOf(diet, tasks)))
    }
    @Test fun classifierThrows_dietDefault() = runTest {
        val r = DefaultCaptureRouter(FakeClassifier { error("boom") }).route(input, listOf(diet, tasks))
        assertEquals(listOf(dietDefault), r)
    }
    @Test fun slowClassifier_timesOutToDefault() = runTest {
        val c = FakeClassifier { delay(10_000); listOf(CandidateAction(ModuleId.TASKS, "log_tasks", 0.9f)) }
        val r = DefaultCaptureRouter(c).route(input, listOf(diet, tasks))
        assertEquals(listOf(dietDefault), r)
        assertTrue(currentTime <= 1_500, "took $currentTime ms")
        assertEquals(1, c.calls)
    }
    @Test fun unknownModule_filteredOut() = runTest {
        val sleep = CandidateAction(ModuleId.SLEEP, "log_sleep", 0.95f)
        assertEquals(listOf(dietDefault), DefaultCaptureRouter(FakeClassifier { listOf(sleep) }).route(input, listOf(diet, tasks)))
    }
}
