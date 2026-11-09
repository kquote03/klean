package app.klean.core.data.module

import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persists which modules are switched off. Lives in `:core:data` so `:core:ui` and features share it. */
interface ModuleGate {
    val disabledIds: Flow<Set<ModuleId>>
    suspend fun isEnabled(id: ModuleId): Boolean
    suspend fun setEnabled(id: ModuleId, enabled: Boolean)

    /**
     * Records every id in [registered] as known; new ones stay on (see [seedNewModules]). Returns the
     * new ids an existing install should list once in "New in Klean".
     */
    suspend fun seedNew(registered: Set<ModuleId>): Set<ModuleId> = emptySet()
}

/** Backed by the synced `core.modules.disabled` setting, so the choice follows the user across devices. */
@Singleton
class SettingsModuleGate @Inject constructor(private val settings: SettingsRepository) : ModuleGate {
    override val disabledIds: Flow<Set<ModuleId>> =
        settings.observe(SyncedKeys.DisabledModules).map { ids -> ids.map(::ModuleId).toSet() }

    override suspend fun isEnabled(id: ModuleId): Boolean = id !in disabledIds.first()

    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) =
        settings.update(SyncedKeys.DisabledModules) { if (enabled) it - id.value else it + id.value }

    /** New modules stay on, so only `known` is written; the disabled set is left as the user made it. */
    override suspend fun seedNew(registered: Set<ModuleId>): Set<ModuleId> {
        val ids = registered.map { it.value }.toSet()
        val known = settings.get(KnownModules)
        if ((ids - known).isEmpty()) return emptySet()
        val result = seedNewModules(ids, known, emptySet())
        settings.update(KnownModules) { k -> k + result.known }
        return result.pending.map(::ModuleId).toSet()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleGateModule {
    @Binds abstract fun gate(impl: SettingsModuleGate): ModuleGate
}
