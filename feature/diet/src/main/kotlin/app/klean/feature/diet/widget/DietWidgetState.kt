package app.klean.feature.diet.widget

import app.klean.feature.diet.data.DietRepository
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

data class WidgetEntry(val id: String, val time: String, val text: String)

data class DietWidgetState(
    val dietEnabled: Boolean = true,
    val count: Int = 0,
    /** Newest first. */
    val entries: List<WidgetEntry> = emptyList(),
    /** A check-in is waiting (WIDGET_HIGHLIGHT format): the pill uses the highlight colour. */
    val highlighted: Boolean = false,
    /** The highlighted check-in's wording, shown in the pill instead of the default prompt. */
    val highlightText: String? = null,
) {
    val lastText: String? get() = entries.firstOrNull()?.text
}

/** Everything the widget shows, as one flow. Kept apart from Glance so it can be unit-tested. */
@OptIn(ExperimentalCoroutinesApi::class)
fun dietWidgetStates(
    repo: DietRepository,
    disabledModules: Flow<Set<ModuleId>>,
    highlightPromptId: Flow<String?>,
    highlightLabel: suspend (promptId: String) -> String? = { null },
    timeLabel: (Long) -> String,
): Flow<DietWidgetState> {
    // Re-evaluates the logical day whenever the sleep window changes; day rollover triggers a widget update.
    val today = repo.quietHours.flatMapLatest { repo.observeDay(repo.todayDate()) }
    return combine(
        disabledModules.map { ModuleId.DIET !in it }.distinctUntilChanged(),
        highlightPromptId.distinctUntilChanged().map { id -> id?.let { (it to highlightLabel(it)) } },
        today,
    ) { enabled, highlight, entries ->
        if (!enabled) {
            DietWidgetState(dietEnabled = false)
        } else {
            DietWidgetState(
                dietEnabled = true,
                count = entries.size,
                entries = entries.sortedByDescending { it.eatenAt }.map { WidgetEntry(it.id, timeLabel(it.eatenAt), it.rawText) },
                highlighted = highlight != null,
                highlightText = highlight?.second,
            )
        }
    }
}
