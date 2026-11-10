package app.klean.feature.sleep

import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGate(off: Set<ModuleId> = emptySet()) : ModuleGate {
    override val disabledIds = MutableStateFlow(off)
    override suspend fun isEnabled(id: ModuleId) = id !in disabledIds.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        disabledIds.value = if (enabled) disabledIds.value - id else disabledIds.value + id
    }
}
