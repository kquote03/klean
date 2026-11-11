package app.klean.shared.capture

import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId

data class CaptureInput(
    val text: String, val source: CaptureSource, val capturedAt: Long,
    val viaVoice: Boolean = false, val photoFile: String? = null, val targetModule: ModuleId? = null,
    /** When the thing happened, if not now (e.g. "Add something" on a gap). UTC ms. */
    val eatenAt: Long? = null,
    /** Module-specific extra fields from the capture sheet (e.g. diet's calorie slot, `diet.kcal`). */
    val extras: Map<String, String> = emptyMap(),
)
data class ActionSpec(val type: String, val description: String)
data class CaptureOption(val moduleId: ModuleId, val action: ActionSpec)
data class CandidateAction(val moduleId: ModuleId, val actionType: String, val confidence: Float, val payloadJson: String? = null)

interface CaptureHandler {
    val moduleId: ModuleId
    val actionSpecs: List<ActionSpec>
    fun defaultAction(input: CaptureInput): CandidateAction
    /** Must persist immediately and return the created record id. Never call AI here. */
    suspend fun execute(input: CaptureInput, action: CandidateAction): String
    suspend fun undo(recordId: String)
    /** Quick-repeat chips shown in the capture sheet. Optional. */
    suspend fun suggestions(): List<String> = emptyList()
}
/** AI-backed classifier. Return null when unavailable. */
interface CaptureClassifier { suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? }
interface CaptureRouter { suspend fun route(input: CaptureInput, handlers: List<CaptureHandler>): List<CandidateAction> }
