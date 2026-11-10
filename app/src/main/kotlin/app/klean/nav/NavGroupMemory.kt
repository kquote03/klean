package app.klean.nav

import app.klean.core.data.prefs.DevicePrefs
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The module last open in each group, so a group's tab lands where the user left it. Device-local. */
interface NavGroupMemory {
    /** Group key → module id. */
    val last: Flow<Map<String, String>>
    suspend fun remember(group: NavGroup, module: String)
}

class DevicePrefsNavGroupMemory @Inject constructor(private val prefs: DevicePrefs) : NavGroupMemory {
    override val last: Flow<Map<String, String>> = prefs.lastInGroup
    override suspend fun remember(group: NavGroup, module: String) = prefs.setLastInGroup(group.key, module)
}

/** For shell tests without Hilt. */
class InMemoryNavGroupMemory(initial: Map<String, String> = emptyMap()) : NavGroupMemory {
    override val last = MutableStateFlow(initial)
    override suspend fun remember(group: NavGroup, module: String) { last.value = last.value + (group.key to module) }
}
