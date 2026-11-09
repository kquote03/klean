package app.klean.today

import app.klean.core.data.prefs.DevicePrefs
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Today's card order and hidden cards. Device-local: each phone keeps its own layout. */
interface TodayLayoutStore {
    val order: Flow<List<String>>
    val hidden: Flow<Set<String>>
    suspend fun setOrder(ids: List<String>)
    suspend fun setHidden(ids: Set<String>)
}

class DevicePrefsTodayLayout @Inject constructor(private val prefs: DevicePrefs) : TodayLayoutStore {
    override val order: Flow<List<String>> = prefs.cardOrder
    override val hidden: Flow<Set<String>> = prefs.hiddenCards
    override suspend fun setOrder(ids: List<String>) = prefs.setCardOrder(ids)
    override suspend fun setHidden(ids: Set<String>) = prefs.setHiddenCards(ids)
}

/** For shell tests without Hilt. */
class InMemoryTodayLayout(order: List<String> = emptyList(), hidden: Set<String> = emptySet()) : TodayLayoutStore {
    override val order = MutableStateFlow(order)
    override val hidden = MutableStateFlow(hidden)
    override suspend fun setOrder(ids: List<String>) { order.value = ids }
    override suspend fun setHidden(ids: Set<String>) { hidden.value = ids }
}
