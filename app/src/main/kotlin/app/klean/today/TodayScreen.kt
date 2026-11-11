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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import kotlinx.coroutines.launch
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
import app.klean.core.design.component.AccountAvatar
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.OverflowMenu
import app.klean.core.design.theme.Space
import app.klean.core.google.AccountSource
import app.klean.core.google.NoAccounts
import app.klean.core.google.ui.rememberAccountPhoto
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

/** What Today's top-bar avatar shows: the current Google account's photo or initial, or a neutral person. */
data class TodayAvatar(val photo: ImageBitmap? = null, val initial: String? = null, val canSwitch: Boolean = false)

/**
 * Today (Soft cards): the weekday, then one card per group (Plan, Track, People) that opens the group. Adding is the
 * Add button; the top-bar avatar opens the account menu (with Settings in it); "Arrange Today" is in the overflow
 * menu. Lives in :app.
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
    account: AccountSource = NoAccounts,
    onAccount: () -> Unit = onSettings,
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
    val accounts by account.state.collectAsStateWithLifecycle(initialValue = app.klean.core.google.AccountsState.None)
    val current = accounts.current
    val photo = rememberAccountPhoto(current?.let { accounts.photos[it.email] })
    val scope = rememberCoroutineScope()
    TodayContent(
        state = state,
        canArrange = hasCards.isNotEmpty(),
        onOpenGroup = onOpenGroup,
        onArrange = onArrange,
        onSettings = onSettings,
        avatar = TodayAvatar(photo, current?.initial, canSwitch = accounts.accounts.size > 1),
        onAccount = onAccount,
        // Gmail's gesture: swipe the avatar to switch accounts. Needing consent opens the menu instead.
        onSwitchAccount = { step -> scope.launch { if (!account.switchBy(step)) onAccount() } },
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
    avatar: TodayAvatar = TodayAvatar(),
    onAccount: () -> Unit = onSettings,
    onSwitchAccount: (Int) -> Unit = {},
) {
    val arrange = stringResource(R.string.today_arrange)
    KleanScreen(
        title = weekday(state.today),
        header = {
            TodayHeader(
                state = state,
                menu = if (canArrange) listOf(MenuAction(arrange, onClick = onArrange)) else emptyList(),
                avatar = avatar,
                onAccount = onAccount,
                onSwitchAccount = onSwitchAccount,
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
private fun TodayHeader(state: TodayState, menu: List<MenuAction>, avatar: TodayAvatar, onAccount: () -> Unit, onSwitchAccount: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.screenEdge, end = Space.xxs, top = Space.md, bottom = Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(weekday(state.today), style = MaterialTheme.typography.displaySmall)
            Text(dayAndMonth(state.today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TopBarAvatar(avatar, onAccount, onSwitchAccount)
        OverflowMenu(menu)
    }
}

/**
 * The account avatar where Google's apps put it (top right): tap for the account menu; with two or more accounts,
 * swipe it down or up for the next or previous one (also offered as accessibility actions).
 */
@Composable
private fun TopBarAvatar(avatar: TodayAvatar, onAccount: () -> Unit, onSwitch: (Int) -> Unit) {
    val threshold = with(LocalDensity.current) { Space.lg.toPx() }
    val next = stringResource(R.string.today_account_next)
    val previous = stringResource(R.string.today_account_previous)
    val swipe = if (avatar.canSwitch) {
        Modifier
            .pointerInput(Unit) {
                var travel = 0f
                detectVerticalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = { if (kotlin.math.abs(travel) >= threshold) onSwitch(if (travel > 0) 1 else -1) },
                ) { change, dy ->
                    change.consume()
                    travel += dy
                }
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(next) { onSwitch(1); true },
                    CustomAccessibilityAction(previous) { onSwitch(-1); true },
                )
            }
    } else {
        Modifier
    }
    AccountAvatar(
        photo = avatar.photo,
        initial = avatar.initial,
        onClick = onAccount,
        contentDescription = stringResource(R.string.today_account),
        modifier = swipe,
    )
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
