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

    /** Switches off every id in [registered] the user has never been offered (see [seedNewModules]). */
    suspend fun seedNew(registered: Set<ModuleId>) {}
}

/** Backed by the synced `core.modules.disabled` setting, so the choice follows the user across devices. */
@Singleton
class SettingsModuleGate @Inject constructor(private val settings: SettingsRepository) : ModuleGate {
    override val disabledIds: Flow<Set<ModuleId>> =
        settings.observe(SyncedKeys.DisabledModules).map { ids -> ids.map(::ModuleId).toSet() }

    override suspend fun isEnabled(id: ModuleId): Boolean = id !in disabledIds.first()

    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) =
        settings.update(SyncedKeys.DisabledModules) { if (enabled) it - id.value else it + id.value }

    /**
     * Disabled is written before known: if the process dies in between, the next start sees the same
     * ids as new again and adds them to the (set-valued, so idempotent) disabled list once more.
     */
    override suspend fun seedNew(registered: Set<ModuleId>) {
        val ids = registered.map { it.value }.toSet()
        val known = settings.get(KnownModules)
        if ((ids - known).isEmpty()) return
        settings.update(SyncedKeys.DisabledModules) { disabled -> seedNewModules(ids, known, disabled).second }
        settings.update(KnownModules) { k -> k + ids }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuleGateModule {
    @Binds abstract fun gate(impl: SettingsModuleGate): ModuleGate
}
