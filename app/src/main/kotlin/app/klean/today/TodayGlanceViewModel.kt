package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.TodayGlance
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

data class TodayState(
    /** The calendar date in the device zone. */
    val today: LocalDate = LocalDate(2026, 1, 1),
    val glance: Glance = EMPTY_GLANCE,
    /** False until the first emission, so an empty day isn't shown while the modules load. */
    val loaded: Boolean = false,
    /** True when no module is shown on Today (all off or hidden). */
    val noModules: Boolean = false,
)

val EMPTY_GLANCE = Glance(emptyList(), GlanceSection(emptyList()), GlanceSection(emptyList()), GlanceSection(emptyList()))

/**
 * Today's whiteboard: the date and the merged [Glance] of the [visible] modules (Arrange Today's order and choice;
 * modules switched off are already dropped by [TodayGlance]'s gate). [ticks] re-read the clock, so the date turns at
 * midnight and "coming up" lets go of what has started.
 */
class TodayGlanceViewModel(
    private val glance: TodayGlance,
    visible: Flow<List<ModuleId>>,
    clock: Clock,
    zone: ZoneProvider,
    ticks: Flow<Unit>,
) : ViewModel() {
    private val now = ticks.map { clock.now() }

    val state: StateFlow<TodayState> = combine(now, visible, glance.byModule()) { t, modules, parts ->
        TodayState(
            today = t.toLocalDateTime(zone.current()).date,
            glance = buildGlance(modules, parts, t.toEpochMilliseconds()),
            loaded = true,
            noModules = modules.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** A row ticked on Today (a task): done in its module, which then drops it from "coming up". */
    fun check(row: GlanceRow) {
        viewModelScope.launch { runCatching { glance.check(row) } }
    }
}
