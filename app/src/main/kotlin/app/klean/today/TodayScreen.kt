package app.klean.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.OverflowMenu
import app.klean.core.design.theme.Space
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.TodayItems
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.nav.NavGroup
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

/**
 * Today (Soft cards): the weekday, then one card per group (Plan, Track, People) that opens the group. Adding is the
 * Add button; Settings is the top-bar button; "Arrange Today" is in the overflow menu. Lives in :app.
 */
@Composable
fun TodayScreen(
    registry: ModuleRegistry,
    dashboard: DashboardContributions,
    todayItems: TodayItems,
    clock: Clock,
    zone: ZoneProvider,
    ticker: MinuteTicker,
    onOpenGroup: (NavGroup) -> Unit,
    layout: TodayLayoutStore = InMemoryTodayLayout(),
    onArrange: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val visibleFlow = remember(registry, layout) {
        combine(registry.enabled, registry.dashboardCards, layout.order, layout.hidden) { on, cards, order, hidden ->
            // Modules without a card of their own (Reminders) still feed their group's card.
            arrangeTodayCards(cards, order, hidden) + on.filter { it.dashboardSlot == null }
        }
    }
    val vm: TodayDashboardViewModel = viewModel {
        TodayDashboardViewModel(dashboard, todayItems, visibleFlow, clock, zone, ticker.ticks())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCards by registry.dashboardCards.collectAsStateWithLifecycle(initialValue = emptyList())
    TodayContent(
        state = state,
        canArrange = hasCards.isNotEmpty(),
        onOpenGroup = onOpenGroup,
        onArrange = onArrange,
        onSettings = onSettings,
    )
}

/** Today without its data sources, for screenshot tests. */
@Composable
fun TodayContent(
    state: TodayState,
    canArrange: Boolean,
    onOpenGroup: (NavGroup) -> Unit,
    onArrange: () -> Unit,
    onSettings: () -> Unit,
) {
    val arrange = stringResource(R.string.today_arrange)
    KleanScreen(
        title = weekday(state.today),
        header = {
            TodayHeader(
                state = state,
                menu = if (canArrange) listOf(MenuAction(arrange, onClick = onArrange)) else emptyList(),
                onSettings = onSettings,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
            items(state.approvals, key = { "m/" + it.id.value }) { m -> ApprovalsCard(m) }
            if (state.loaded && state.cards.isEmpty() && state.approvals.isEmpty()) {
                item(key = "empty") {
                    EmptyState(stringResource(R.string.today_empty), action = MenuAction(stringResource(R.string.nav_settings), onClick = onSettings))
                }
            }
            items(state.cards, key = { it.group.name }) { card -> GroupCard(card) { onOpenGroup(card.group) } }
        }
    }
}

/** Delegation's approvals lead Today when there are any; the card opens each approval itself. */
@Composable
private fun ApprovalsCard(module: KleanModule) = module.DashboardCard(onOpen = {}, modifier = Modifier)

@Composable
private fun TodayHeader(state: TodayState, menu: List<MenuAction>, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.screenEdge, end = Space.xxs, top = Space.md, bottom = Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(weekday(state.today), style = MaterialTheme.typography.displaySmall)
            Text(dayAndMonth(state.today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(Space.touchTarget)) {
            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.nav_settings))
        }
        OverflowMenu(menu)
    }
}

@Composable
private fun GroupCard(card: TodayCard, onOpen: () -> Unit) {
    val tone = when (card.group) {
        NavGroup.PLAN -> CardTone.PRIMARY
        NavGroup.TRACK -> CardTone.SECONDARY
        else -> CardTone.TERTIARY
    }
    val groupName = stringResource(
        when (card.group) {
            NavGroup.PLAN -> R.string.nav_plan
            NavGroup.TRACK -> R.string.nav_track
            else -> R.string.nav_people
        },
    )
    val label = card.label ?: stringResource(R.string.today_card_next)
    val fact = card.fact ?: stringResource(R.string.today_card_clear)
    val dot = dotColor(tone)
    ModuleCard(
        label = label,
        fact = fact,
        supporting = card.supporting,
        tone = tone,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.today_card_open, groupName),
        body = if (card.rows.isEmpty()) {
            null
        } else {
            {
                card.rows.forEach { row ->
                    ListRow(
                        title = row.text,
                        meta = row.meta,
                        dense = true,
                        titleMaxLines = 1,
                        leading = { RowMark(row, dot) },
                    )
                }
            }
        },
    )
}

@Composable
private fun RowMark(row: TodayRow, dot: Color) {
    val icon = row.icon
    when {
        row.isTask -> Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        icon != null -> Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Box(Modifier.size(Space.xs).background(dot, CircleShape))
    }
}

@Composable
private fun dotColor(tone: CardTone): Color = when (tone) {
    CardTone.PRIMARY -> MaterialTheme.colorScheme.primary
    CardTone.SECONDARY -> MaterialTheme.colorScheme.secondary
    CardTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
}

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, month.ordinal + 1, day)

/** "Wednesday" in the user's locale. */
internal fun weekday(d: LocalDate): String = d.toJava().format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))

/** "11 November" / "November 11", whichever the locale prefers. */
internal fun dayAndMonth(d: LocalDate): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMM")
    return d.toJava().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
