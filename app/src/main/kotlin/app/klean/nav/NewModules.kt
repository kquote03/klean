package app.klean.nav

import app.klean.core.data.prefs.DevicePrefs
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Modules an update added, waiting to be listed once in "New in Klean". Device-local. */
interface NewModulesStore {
    val pending: Flow<Set<ModuleId>>
    suspend fun add(ids: Set<ModuleId>)
    suspend fun clear()
}

class DevicePrefsNewModules @Inject constructor(private val prefs: DevicePrefs) : NewModulesStore {
    override val pending: Flow<Set<ModuleId>> = prefs.pendingNewModules.map { s -> s.map(::ModuleId).toSet() }
    override suspend fun add(ids: Set<ModuleId>) {
        if (ids.isEmpty()) return
        prefs.setPendingNewModules(prefs.pendingNewModules.first() + ids.map { it.value })
    }
    override suspend fun clear() = prefs.setPendingNewModules(emptySet())
}

/** For shell tests without Hilt. */
class InMemoryNewModules(initial: Set<ModuleId> = emptySet()) : NewModulesStore {
    override val pending = MutableStateFlow(initial)
    override suspend fun add(ids: Set<ModuleId>) { pending.value = pending.value + ids }
    override suspend fun clear() { pending.value = emptySet() }
}
