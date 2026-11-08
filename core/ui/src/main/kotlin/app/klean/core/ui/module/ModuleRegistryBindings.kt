package app.klean.core.ui.module

import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Declares the set so the app builds even when no feature contributes a module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleSetModule {
    @Multibinds abstract fun kleanModules(): Set<KleanModule>
}

/**
 * Temporary in-memory gate: every module starts enabled and the choice is lost on restart.
 * Replaced by the DataStore-backed gate in `:core:data` (M3); delete this file and
 * [TemporaryModuleGateModule] then.
 */
@Singleton
class InMemoryModuleGate @Inject constructor() : ModuleGate {
    private val state = MutableStateFlow<Set<ModuleId>>(emptySet())
    override val disabledIds: Flow<Set<ModuleId>> = state
    override suspend fun isEnabled(id: ModuleId) = id !in state.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        state.value = if (enabled) state.value - id else state.value + id
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TemporaryModuleGateModule {
    @Binds abstract fun gate(impl: InMemoryModuleGate): ModuleGate
}
