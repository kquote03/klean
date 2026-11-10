package app.klean.watch

import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureRouter
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant

/** A Data Layer that records what the phone would have sent (stands in for DataClient / MessageClient). */
class FakeWearDataLayer(var works: Boolean = true) : WearDataLayer {
    val puts = mutableListOf<ByteArray>()
    val messages = mutableListOf<Pair<String, ByteArray>>()
    override suspend fun putState(bytes: ByteArray): Boolean {
        if (!works) throw IllegalStateException("no Data Layer")
        puts += bytes
        return true
    }
    override suspend fun sendToWatches(path: String, bytes: ByteArray): Int {
        if (!works) throw IllegalStateException("no Data Layer")
        messages += path to bytes
        return 1
    }
}

class RecordingHandler(override val moduleId: ModuleId) : CaptureHandler {
    val executed = mutableListOf<CaptureInput>()
    override val actionSpecs = listOf(ActionSpec("add", "anything"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, "add", 1f)
    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        executed += input
        return "${moduleId.value}-${executed.size}"
    }
    override suspend fun undo(recordId: String) {}
}

/** The target module when there is one, otherwise the first handler. */
class TargetRouter : CaptureRouter {
    override suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>): List<CandidateAction> {
        val h = handlers.firstOrNull { it.moduleId == input.targetModule } ?: handlers.firstOrNull() ?: return emptyList()
        return listOf(h.defaultAction(input))
    }
}

class FakeWatchWorkouts : WatchWorkouts {
    val started = mutableListOf<String>()
    val sets = mutableListOf<List<Any?>>()
    var knownTemplates = setOf("tpl-1")
    var openSession: String? = "s1"
    override suspend fun start(templateId: String): Boolean {
        if (templateId !in knownTemplates) return false
        started += templateId
        return true
    }
    override suspend fun logSet(sessionId: String, exercise: String, reps: Int, weightKg: Double?): Boolean {
        if (sessionId != openSession) return false
        sets += listOf(sessionId, exercise, reps, weightKg)
        return true
    }
}

class SteppingClock(var ms: Long = 1_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}
