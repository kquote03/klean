package app.klean.core.ai

import app.klean.shared.ai.AiTask
import app.klean.shared.capture.CaptureClassifier
import app.klean.shared.capture.CaptureOption
import app.klean.shared.capture.CandidateAction
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Suggests which module a quick capture belongs to. Only uses a provider with [Capability.OFFLINE],
 * because capture must never wait on the network ("never block a log"); otherwise returns null and the
 * router saves to the default module at once. With one module installed the router never calls it.
 */
class AiCaptureClassifier @Inject constructor(private val ai: AiService) : CaptureClassifier {
    override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? {
        if (options.isEmpty()) return null
        val p = ai.providerFor(AiTask.CAPTURE_ROUTING) ?: return null
        if (Capability.OFFLINE !in p.capabilities()) return null
        val system = "Decide where a short note belongs. Options, as moduleId/actionType: description:\n" +
            options.joinToString("\n") { "- ${it.moduleId.value}/${it.action.type}: ${it.action.description}" } +
            "\nGive every plausible option a confidence from 0 to 1. Reply with JSON only."
        val out = ai.runWith(p, StructuredRequest(system, text, SCHEMA), sendImage = false) as? AiOutcome.Success ?: return null
        val known = options.map { it.moduleId to it.action.type }.toSet()
        val list = ((out.json as? JsonObject)?.get("candidates") as? JsonArray) ?: return null
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val module = (o["moduleId"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val action = (o["actionType"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val confidence = (o["confidence"] as? JsonPrimitive)?.doubleOrNull?.toFloat()?.coerceIn(0f, 1f) ?: return@mapNotNull null
            if (ModuleId(module) to action !in known) null else CandidateAction(ModuleId(module), action, confidence)
        }.sortedByDescending { it.confidence }
    }

    companion object {
        val SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("candidates") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("moduleId") { put("type", "string") }
                            putJsonObject("actionType") { put("type", "string") }
                            putJsonObject("confidence") { put("type", "number") }
                        }
                        putJsonArray("required") { add("moduleId"); add("actionType"); add("confidence") }
                        put("additionalProperties", false)
                    }
                }
            }
            putJsonArray("required") { add("candidates") }
            put("additionalProperties", false)
        }
    }
}
