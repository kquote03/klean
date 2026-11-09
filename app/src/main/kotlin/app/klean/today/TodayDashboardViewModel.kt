package app.klean.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.SummaryItem
import java.time.DayOfWeek
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime

data class WeekDay(val date: LocalDate, val count: Int, val route: Any?, val isToday: Boolean, val isFuture: Boolean)

data class TodayDashboardState(
    val greeting: Greeting = Greeting.MORNING,
    /**
     * The calendar date in the device zone. Diet counts by logical day (it turns at the end of the
     * sleep window), so between midnight and ~04:00 a snack shows on the previous day's cell.
     */
    val today: LocalDate = LocalDate(2026, 1, 1),
    val summaries: List<SummaryItem> = emptyList(),
    val showWeek: Boolean = false,
    val week: List<WeekDay> = emptyList(),
    val weekOffset: Int = 0,
    val canGoNext: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * Header, tiles and week overview on Today. Capture stays in [TodayViewModel].
 * [ticks] re-reads the clock (once a minute in the app), so the greeting and the week follow the
 * time of day; the week's activity is only re-queried when the date or the shown week changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardViewModel(
    private val contributions: DashboardContributions,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val firstDay: DayOfWeek,
    ticks: Flow<Unit>,
) : ViewModel() {
    private val offset = MutableStateFlow(0)

    private val now: Flow<LocalDateTime> = ticks
        .map { clock.now().toLocalDateTime(zone.current()) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val week: Flow<Pair<Int, List<WeekDay>>> =
        combine(offset, now.map { it.date }.distinctUntilChanged()) { off, today -> off to today }
            .flatMapLatest { (off, today) ->
                val days = weekOf(today, firstDay, off)
                contributions.activity(days.first(), days.last()).map { activity ->
                    off to days.map { d ->
                        WeekDay(d, activity[d]?.count ?: 0, activity[d]?.route, isToday = d == today, isFuture = d > today)
                    }
                }
            }

    val state: StateFlow<TodayDashboardState> = combine(
        now,
        contributions.summaries(),
        contributions.hasActivityProviders(),
        week,
    ) { now, tiles, hasWeek, (off, days) ->
        TodayDashboardState(
            greeting = greetingFor(now.hour),
            today = now.date,
            summaries = tiles,
            showWeek = hasWeek,
            week = days,
            weekOffset = off,
            canGoNext = off < 0,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayDashboardState())

    fun previousWeek() = offset.update { it - 1 }
    fun nextWeek() = offset.update { clampWeekOffset(it + 1) }
}
