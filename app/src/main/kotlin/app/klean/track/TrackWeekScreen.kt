package app.klean.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.FactCard
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.WeekBars
import app.klean.core.design.theme.Space
import app.klean.core.ui.module.WeekSummaries
import app.klean.core.ui.module.WeekSummary
import app.klean.shared.module.ModuleId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

data class TrackWeekState(
    /** The last seven days, oldest first, ending today. */
    val days: List<LocalDate> = emptyList(),
    val summaries: List<WeekSummary> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Sleep, then workouts, then food: the prototype's order, whatever order the providers come in. */
    val ordered: List<WeekSummary> get() = summaries.sortedBy { ORDER.indexOf(it.moduleId).let { i -> if (i < 0) ORDER.size else i } }

    /** The first tracker with a week of bars leads the page; the rest sit under it as small cards. */
    val hero: WeekSummary? get() = ordered.firstOrNull { it.bars != null }
    val rest: List<WeekSummary> get() = ordered.filter { it != hero }

    private companion object {
        val ORDER = listOf(ModuleId.SLEEP, ModuleId.WORKOUT, ModuleId.DIET)
    }
}

/** The last seven days ending on [today], oldest first. */
fun lastSevenDays(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }

/** Track › Week: every enabled tracker's week, re-read when the date turns ([ticks]). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackWeekViewModel(summaries: WeekSummaries, clock: Clock, zone: ZoneProvider, ticks: Flow<Unit>) : ViewModel() {
    val state: StateFlow<TrackWeekState> = ticks
        .map { clock.now().toLocalDateTime(zone.current()).date }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val days = lastSevenDays(today)
            summaries.week(days).map { TrackWeekState(days, it, loaded = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackWeekState())
}

@Composable
fun TrackWeekScreen(summaries: WeekSummaries, clock: Clock, zone: ZoneProvider, ticks: () -> Flow<Unit>) {
    val vm: TrackWeekViewModel = viewModel { TrackWeekViewModel(summaries, clock, zone, ticks()) }
    val state by vm.state.collectAsStateWithLifecycle()
    TrackWeekContent(state)
}

/**
 * A neutral summary, not a dashboard of links: the segments above already open each tracker (design-system.md,
 * clutter rule 7). One hero with the week's bars, then one small card per other tracker, two to a row.
 */
@Composable
fun TrackWeekContent(state: TrackWeekState) {
    KleanScreen(title = stringResource(R.string.nav_track)) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
            if (state.loaded && state.summaries.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.track_week_empty), icon = Icons.Rounded.Insights) }
                return@LazyColumn
            }
            state.hero?.let { hero ->
                item(key = "hero") {
                    val bars = hero.bars.orEmpty()
                    ModuleCard(
                        label = stringResource(R.string.track_week_hero),
                        fact = hero.fact,
                        supporting = hero.supporting,
                        tone = CardTone.SECONDARY,
                        hero = {
                            WeekBars(
                                values = bars,
                                labels = state.days.map { java.time.DayOfWeek.of(it.dayOfWeek.isoDayNumber).getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                                today = bars.lastIndex,
                                description = hero.barsDescription ?: hero.fact,
                            )
                        },
                    )
                }
            }
            state.rest.chunked(2).forEachIndexed { i, pair ->
                item(key = "row$i") {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                        pair.forEach { s ->
                            FactCard(s.label, s.fact, Modifier.weight(1f).fillMaxSize(), supporting = s.supporting)
                        }
                        if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
