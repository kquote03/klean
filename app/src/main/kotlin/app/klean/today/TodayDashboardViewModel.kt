package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.TodayItems
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

data class TodayState(
    /** The calendar date in the device zone. */
    val today: LocalDate = LocalDate(2026, 1, 1),
    val cards: List<TodayCard> = emptyList(),
    /** Modules whose own card leads Today (Delegation's approvals). */
    val approvals: List<KleanModule> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Today's date and its group cards. [visible] is the arranged, shown modules (Arrange Today) plus the modules that
 * have no card of their own; [ticks] re-read the clock, so the date turns at midnight.
 */
class TodayDashboardViewModel(
    contributions: DashboardContributions,
    todayItems: TodayItems,
    visible: Flow<List<KleanModule>>,
    clock: Clock,
    zone: ZoneProvider,
    ticks: Flow<Unit>,
) : ViewModel() {
    private val date = ticks.map { clock.now().toLocalDateTime(zone.current()).date }.distinctUntilChanged()

    val state: StateFlow<TodayState> = combine(
        date,
        visible,
        contributions.summariesByModule(),
        todayItems.itemsByModule(),
    ) { today, modules, summaries, items ->
        TodayState(
            today = today,
            cards = todayCards(modules.map { it.id }, summaries, items),
            approvals = modules.filter { it.dashboardSlot == DashboardSlot.APPROVALS },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())
}
