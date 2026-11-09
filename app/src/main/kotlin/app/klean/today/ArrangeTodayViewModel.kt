package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.arrangeCards
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArrangeRow(val module: KleanModule, val visible: Boolean)

/** Moves Today's cards up or down and shows or hides them. Every change is saved at once. */
class ArrangeTodayViewModel(
    registry: ModuleRegistry,
    private val layout: TodayLayoutStore,
) : ViewModel() {
    val rows: StateFlow<List<ArrangeRow>> = combine(registry.dashboardCards, layout.order, layout.hidden) { cards, order, hidden ->
        arrangeTodayCards(cards, order, emptySet()).map { ArrangeRow(it, it.id.value !in hidden) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun moveUp(id: String) = move(id, -1)
    fun moveDown(id: String) = move(id, +1)

    fun setVisible(id: String, visible: Boolean) {
        viewModelScope.launch {
            val hidden = layout.hidden.first()
            layout.setHidden(if (visible) hidden - id else hidden + id)
        }
    }

    private fun move(id: String, by: Int) {
        viewModelScope.launch {
            val ids = rows.value.map { it.module.id.value }.toMutableList()
            val i = ids.indexOf(id)
            val j = i + by
            if (i < 0 || j !in ids.indices) return@launch
            ids[i] = ids[j].also { ids[j] = ids[i] }
            // Keep ids of cards that are switched off right now, so their place survives.
            val rest = layout.order.first().filter { it !in ids }
            layout.setOrder(ids + rest)
        }
    }
}

/** [arrangeCards] for modules: ids are module ids, slots come from [KleanModule.dashboardSlot]. */
fun arrangeTodayCards(cards: List<KleanModule>, order: List<String>, hidden: Set<String>): List<KleanModule> =
    arrangeCards(cards, { it.id.value }, { it.dashboardSlot?.ordinal ?: Int.MAX_VALUE }, order, hidden)
