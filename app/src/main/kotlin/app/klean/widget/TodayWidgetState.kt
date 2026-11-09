package app.klean.widget

import app.klean.core.ui.module.TodayItem

/** What the Today widget shows: the next few items across modules, and how many there are in all. */
data class TodayWidgetState(val rows: List<TodayItem>, val total: Int) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** [items] already come soonest first and gate-filtered (`TodayItems`); this caps them for the widget. */
fun todayWidgetState(items: List<TodayItem>, max: Int = MAX_ROWS): TodayWidgetState =
    TodayWidgetState(items.sortedBy { it.sortKey }.take(max), items.size)

const val MAX_ROWS = 5
