package app.klean.core.capture

import app.klean.core.data.module.ModuleGate
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeModuleGate(initial: Set<ModuleId> = emptySet()) : ModuleGate {
    val state = MutableStateFlow(initial)
    override val disabledIds: Flow<Set<ModuleId>> = state
    override suspend fun isEnabled(id: ModuleId) = id !in state.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        state.value = if (enabled) state.value - id else state.value + id
    }
}

class FakeHandler(override val moduleId: ModuleId, var repeats: List<String> = emptyList()) : CaptureHandler {
    val executed = mutableListOf<CaptureInput>()
    val undone = mutableListOf<String>()
    override val actionSpecs = listOf(ActionSpec("add", "anything"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, "add", 1f)
    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        executed += input
        return "${moduleId.value}-${executed.size}"
    }
    override suspend fun undo(recordId: String) { undone += recordId }
    override suspend fun suggestions() = repeats
}
