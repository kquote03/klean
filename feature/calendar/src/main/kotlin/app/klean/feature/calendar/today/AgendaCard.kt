package app.klean.feature.calendar.today

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.KleanCard
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.calendar.R
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.feature.calendar.ui.metaLabel
import app.klean.feature.calendar.ui.rememberCalendarFormat
import app.klean.feature.calendar.ui.timeLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toLocalDateTime

data class AgendaCardState(val items: List<AgendaItem> = emptyList(), val loaded: Boolean = false)

/**
 * Today's "Agenda" card: today's events (Klean's and the phone's), reminders and birthdays still ahead, at most
 * [MAX]. Tasks have their own card.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaCardViewModel @Inject constructor(
    agenda: AgendaSource,
    private val clock: Clock,
    private val zone: ZoneProvider,
    ticker: CalendarTicker,
) : ViewModel() {
    val state: StateFlow<AgendaCardState> = ticker.ticks()
        .map { clock.now().toLocalDateTime(zone.current()).date }
        .distinctUntilChanged()
        .flatMapLatest { day -> combine(agenda.items(day, day), ticker.ticks()) { items, _ -> day to items } }
        .map { (day, items) ->
            val now = clock.now().toEpochMilliseconds()
            AgendaCardState(
                items = items.filter { it.kind != AgendaKind.TASK && it.isOn(day) && (it.allDay || maxOf(it.end, it.start) >= now) }.take(MAX),
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaCardState())

    companion object {
        const val MAX = 5
    }
}

@Composable
fun AgendaCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: AgendaCardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberCalendarFormat()
    KleanCard(title = stringResource(R.string.calendar_card_title), onOpen = onOpen, modifier = modifier) {
        if (state.loaded && state.items.isEmpty()) {
            NeutralEmpty(stringResource(R.string.calendar_card_empty), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        state.items.forEach { item ->
            val meta = metaLabel(item)
            EntryRow(
                time = timeLabel(item, fmt, stacked = true),
                text = item.title,
                meta = meta?.let { m -> { Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                onClick = onOpen,
            )
        }
    }
}
