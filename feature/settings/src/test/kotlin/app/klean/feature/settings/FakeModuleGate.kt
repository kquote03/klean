package app.klean.feature.settings

import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ModuleGate] for tests. */
class FakeModuleGate(initial: Set<ModuleId> = emptySet()) : ModuleGate {
    val state = MutableStateFlow(initial)
    override val disabledIds: Flow<Set<ModuleId>> = state
    override suspend fun isEnabled(id: ModuleId) = id !in state.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        state.value = if (enabled) state.value - id else state.value + id
    }
}
