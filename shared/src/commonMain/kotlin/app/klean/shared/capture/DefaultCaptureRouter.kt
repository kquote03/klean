package app.klean.shared.capture

import app.klean.shared.module.ModuleId
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

class DefaultCaptureRouter(
    private val classifier: CaptureClassifier?,
    private val defaultModule: ModuleId = ModuleId.DIET,
    private val timeoutMs: Long = 1_500,
    private val minConfidence: Float = 0.5f,
) : CaptureRouter {
    override suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>): List<CandidateAction> {
        if (handlers.isEmpty()) return emptyList()
        input.targetModule?.let { t -> handlers.firstOrNull { it.moduleId == t }?.let { return listOf(it.defaultAction(input)) } }
        val fallback = (handlers.firstOrNull { it.moduleId == defaultModule } ?: handlers.first()).defaultAction(input)
        if (handlers.size == 1 || classifier == null) return listOf(fallback)
        val options = handlers.flatMap { h -> h.actionSpecs.map { CaptureOption(h.moduleId, it) } }
        val known = handlers.map { it.moduleId }.toSet()
        val ai = try {
            withTimeoutOrNull(timeoutMs) { classifier.classify(input.text, options) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            ?.filter { it.moduleId in known }?.sortedByDescending { it.confidence }.orEmpty()
        if (ai.isEmpty() || ai.first().confidence < minConfidence)
            return listOf(fallback) + ai.filterNot { it.moduleId == fallback.moduleId }
        return ai
    }
}
