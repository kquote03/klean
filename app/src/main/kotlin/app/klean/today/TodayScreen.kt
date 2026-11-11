package app.klean.today

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.AccountAvatar
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.OverflowMenu
import app.klean.core.design.component.QuickActionGrid
import app.klean.core.design.component.QuickActionUi
import app.klean.core.design.component.TimelineRow
import app.klean.core.design.theme.Space
import app.klean.core.google.AccountSource
import app.klean.core.google.NoAccounts
import app.klean.core.google.ui.rememberAccountPhoto
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.ui.MinuteTicker
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import app.klean.core.design.component.GlanceSection as SectionCard

/** What Today's top-bar avatar shows: the current Google account's photo or initial, or a neutral person. */
data class TodayAvatar(val photo: ImageBitmap? = null, val initial: String? = null, val canSwitch: Boolean = false)

/** How long an instant action's Undo stays on screen (Sleep's "Good night · Undo"). */
const val INSTANT_UNDO_MS = 6_000L

/**
 * Today, the whiteboard of the day (plan 2026-11-11): the weekday, a one-tap grid (Speak, Type, then one tile per
 * module in Arrange Today's order), then Needs attention, Coming up and So far today, each ordered by time across
 * modules. There is no Add button here: the grid replaces it. The avatar opens the account menu; Arrange Today is in
 * the overflow menu. Lives in :app.
 */
@Composable
fun TodayScreen(
    registry: ModuleRegistry,
    glance: TodayGlance,
    clock: Clock,
    zone: ZoneProvider,
    ticker: MinuteTicker,
    onQuickAction: (QuickAction) -> Unit,
    onVoice: () -> Unit,
    onType: () -> Unit,
    onOpen: (String) -> Unit,
    layout: TodayLayoutStore = InMemoryTodayLayout(),
    onArrange: () -> Unit = {},
    onSettings: () -> Unit = {},
    account: AccountSource = NoAccounts,
    onAccount: () -> Unit = onSettings,
) {
    val visibleFlow = remember(registry, layout) {
        combine(registry.enabled, registry.dashboardCards, layout.order, layout.hidden) { on, cards, order, hidden ->
            // Arrange Today orders the modules with a place on Today; the rest (Reminders) follow in nav order.
            (arrangeTodayCards(cards, order, hidden) + on.filter { it.dashboardSlot == null }).map { it.id }
        }
    }
    val vm: TodayGlanceViewModel = viewModel { TodayGlanceViewModel(glance, visibleFlow, clock, zone, ticker.ticks()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val hasCards by registry.dashboardCards.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts by account.state.collectAsStateWithLifecycle(initialValue = app.klean.core.google.AccountsState.None)
    val current = accounts.current
    val photo = rememberAccountPhoto(current?.let { accounts.photos[it.email] })
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    TodayContent(
        state = state,
        canArrange = hasCards.isNotEmpty(),
        onQuickAction = onQuickAction,
        onVoice = onVoice,
        onType = onType,
        onOpen = onOpen,
        onCheck = vm::check,
        onArrange = onArrange,
        onSettings = onSettings,
        avatar = TodayAvatar(photo, current?.initial, canSwitch = accounts.accounts.size > 1),
        onAccount = onAccount,
        // Gmail's gesture: swipe the avatar to switch accounts. Needing consent opens the menu instead.
        onSwitchAccount = { step -> scope.launch { if (!account.switchBy(step)) onAccount() } },
        timeOf = remember(ctx, zone) { deviceTime(ctx, zone) },
    )
}

/** "1:15 PM" or "13:15", as the phone is set, in the user's zone. */
private fun deviceTime(ctx: android.content.Context, zone: ZoneProvider): (Long) -> String = { ms ->
    val f = android.text.format.DateFormat.getTimeFormat(ctx)
    f.timeZone = java.util.TimeZone.getTimeZone(zone.current().id)
    f.format(java.util.Date(ms))
}

/**
 * Today without its data sources, for tests. A [QuickAction.Instant] runs here and answers with a snackbar and Undo;
 * the other actions go to [onQuickAction].
 */
@Composable
fun TodayContent(
    state: TodayState,
    canArrange: Boolean,
    onQuickAction: (QuickAction) -> Unit,
    onVoice: () -> Unit,
    onType: () -> Unit,
    onOpen: (String) -> Unit,
    onArrange: () -> Unit,
    onSettings: () -> Unit,
    onCheck: (GlanceRow) -> Unit = {},
    avatar: TodayAvatar = TodayAvatar(),
    onAccount: () -> Unit = onSettings,
    onSwitchAccount: (Int) -> Unit = {},
    timeOf: (Long) -> String = { ms -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms)) },
) {
    val arrange = stringResource(R.string.today_arrange)
    val undo = stringResource(R.string.today_undo)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val g = state.glance
    val tiles = listOf(
        QuickActionUi(stringResource(R.string.today_quick_voice), Icons.Rounded.Mic, onClick = onVoice),
        QuickActionUi(stringResource(R.string.today_quick_type), Icons.Rounded.Keyboard, stringResource(R.string.today_quick_type_description), onClick = onType),
    ) + g.quickActions.map { a ->
        QuickActionUi(a.label, a.icon) {
            if (a is QuickAction.Instant) {
                scope.launch {
                    val r = a.run() ?: return@launch
                    // Undo stays for a few seconds, then the snackbar leaves on its own.
                    val result = withTimeoutOrNull(INSTANT_UNDO_MS) {
                        snackbar.showSnackbar(r.message, actionLabel = if (r.undo != null) undo else null, duration = SnackbarDuration.Indefinite)
                    }
                    if (result == SnackbarResult.ActionPerformed) r.undo?.invoke()
                }
            } else {
                onQuickAction(a)
            }
        }
    }
    KleanScreen(
        title = weekday(state.today),
        showAdd = false,
        snackbarHostState = snackbar,
        header = {
            TodayHeader(
                today = state.today,
                menu = if (canArrange) listOf(MenuAction(arrange, onClick = onArrange)) else emptyList(),
                avatar = avatar,
                onAccount = onAccount,
                onSwitchAccount = onSwitchAccount,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            item(key = "quick") { QuickActionGrid(tiles) }
            if (state.loaded && state.noModules) {
                item(key = "empty") {
                    EmptyState(stringResource(R.string.today_empty), action = MenuAction(stringResource(R.string.nav_settings), onClick = onSettings))
                }
                return@LazyColumn
            }
            if (!state.loaded) return@LazyColumn
            item(key = "attention") {
                Section(stringResource(R.string.today_attention), g.attention, empty = null, dimmed = false, timeOf, onOpen, onCheck)
            }
            item(key = "upcoming") {
                Section(stringResource(R.string.today_upcoming), g.upcoming, stringResource(R.string.today_upcoming_empty), dimmed = false, timeOf, onOpen, onCheck)
            }
            item(key = "done") {
                Section(stringResource(R.string.today_done), g.done, stringResource(R.string.today_done_empty), dimmed = true, timeOf, onOpen, onCheck)
            }
        }
    }
}

/** One whiteboard section; "+N more" opens the rest in place. */
@Composable
private fun Section(
    title: String,
    section: GlanceSection,
    empty: String?,
    dimmed: Boolean,
    timeOf: (Long) -> String,
    onOpen: (String) -> Unit,
    onCheck: (GlanceRow) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val more = if (section.moreCount > 0 && !expanded) {
        MenuAction(stringResource(R.string.today_more, section.moreCount)) { expanded = true }
    } else {
        null
    }
    val rows = if (expanded) section.rows + section.overflow else section.rows
    SectionCard(
        title = title,
        empty = empty,
        more = more,
        content = if (rows.isEmpty()) {
            null
        } else {
            {
                rows.forEach { r ->
                    TimelineRow(
                        time = r.at?.let(timeOf),
                        title = r.text,
                        detail = r.detail,
                        icon = r.icon,
                        dimmed = dimmed,
                        checkable = r.checkable,
                        onCheck = { onCheck(r) },
                        onClick = r.route?.let { route -> { onOpen(route) } },
                    )
                }
            }
        },
    )
}

@Composable
private fun TodayHeader(today: LocalDate, menu: List<MenuAction>, avatar: TodayAvatar, onAccount: () -> Unit, onSwitchAccount: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Space.screenEdge, end = Space.xxs, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text(weekday(today), style = MaterialTheme.typography.displaySmall)
            Text(dayAndMonth(today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, month.ordinal + 1, day)

/** "Wednesday" in the user's locale. */
internal fun weekday(d: LocalDate): String = d.toJava().format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))

/** "11 November" / "November 11", whichever the locale prefers. */
internal fun dayAndMonth(d: LocalDate): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMM")
    return d.toJava().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
