package app.klean.core.ui.module

import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * One tracker's week on Track › Week (docs/ui/design-system.md): neutral facts, already formatted. [label] names
 * the module's card ("Workouts"), [fact] is its one big fact ("2 sessions"), [supporting] one line under it.
 * [bars] (seven values 0..1, or null for a day with nothing, oldest first) let the card lead the page with a
 * week of bars; [barsDescription] reads them as one item.
 */
data class WeekSummary(
    val moduleId: ModuleId,
    val label: String,
    val fact: String,
    val supporting: String? = null,
    val bars: List<Float?>? = null,
    val barsDescription: String? = null,
)

/** A tracker's contribution to Track › Week. Bind with @IntoSet. Emit null when the week has nothing to say. */
interface WeekSummaryProvider {
    val moduleId: ModuleId

    /** The summary for [days]: the last seven calendar days, oldest first, ending today. */
    fun week(days: List<LocalDate>): Flow<WeekSummary?>
}

/** Every enabled tracker's week, filtered by the module gate, in provider (module id) order. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WeekSummaries @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards WeekSummaryProvider>,
    private val gate: ModuleGate,
) {
    fun week(days: List<LocalDate>): Flow<List<WeekSummary>> = gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
        val on = providers.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
        if (on.isEmpty()) flowOf(emptyList()) else combine(on.map { it.week(days) }) { l -> l.filterNotNull() }
    }
}
