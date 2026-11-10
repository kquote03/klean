package app.klean.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.combine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.R
import app.klean.core.capture.CaptureActivity
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.SummaryTile
import app.klean.core.design.component.WeekDayCell
import app.klean.core.design.component.WeekOverview
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.diet.CaptureSource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

/**
 * Today: a calm welcome (greeting + date), the universal capture field, the modules' summary tiles,
 * a neutral week overview and the module cards. Lives in :app until a second consumer justifies
 * :feature:today.
 */
@Composable
fun TodayScreen(
    registry: ModuleRegistry,
    capture: UniversalCaptureService,
    clock: Clock,
    dashboard: DashboardContributions,
    zone: ZoneProvider,
    ticker: MinuteTicker,
    onOpen: (Any) -> Unit,
    layout: TodayLayoutStore = InMemoryTodayLayout(),
    onArrange: () -> Unit = {},
    vm: TodayViewModel = viewModel { TodayViewModel(capture, clock) },
    dash: TodayDashboardViewModel = viewModel {
        TodayDashboardViewModel(dashboard, clock, zone, WeekFields.of(Locale.getDefault()).firstDayOfWeek, ticker.ticks())
    },
) {
    val cardsFlow = remember(registry, layout) {
        combine(registry.dashboardCards, layout.order, layout.hidden) { c, order, hidden -> arrangeTodayCards(c, order, hidden) }
    }
    val cards by cardsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasCards by registry.dashboardCards.collectAsStateWithLifecycle(initialValue = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    val available by vm.captureAvailable.collectAsStateWithLifecycle()
    val justSaved by vm.justSaved.collectAsStateWithLifecycle()
    val state by dash.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        LazyColumn(
            // The list stops below the status bar, so scrolled cards never run under its icons.
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 16.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                        Text(greetingText(state.greeting), style = MaterialTheme.typography.headlineMedium)
                        if (state.loaded) {
                            Text(
                                longDate(state.today),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasCards.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.today_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.today_arrange)) },
                                    onClick = { menuOpen = false; onArrange() },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "capture") {
                Column {
                    CaptureField(
                        value = text,
                        onValueChange = { text = it },
                        onSubmit = {
                            val submitted = text
                            vm.submit(submitted) { saved -> if (saved && text == submitted) text = "" }
                        },
                        // Voice runs in the capture sheet, which owns the mic permission and the Undo chip.
                        onMic = { ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.DASHBOARD, startVoice = true)) },
                        onPhoto = null,
                        placeholder = stringResource(R.string.today_capture_placeholder),
                        enabled = available,
                    )
                    if (justSaved) {
                        Text(
                            stringResource(R.string.today_capture_saved),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        )
                    }
                }
            }
            if (state.summaries.isNotEmpty()) {
                item(key = "tiles") {
                    // Rows of tiles at least 150 dp wide. Tiles in a row share its height (the tallest
                    // value decides), so a two-line value doesn't leave its neighbour short.
                    BoxWithConstraints {
                        // Larger text needs wider tiles: one column at big font scales instead of cut-off values.
                        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
                        val perRow = (maxWidth / (158.dp * scale)).toInt().coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.summaries.chunked(perRow).forEach { row ->
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { tile ->
                                        SummaryTile(
                                            label = tile.label,
                                            value = tile.value,
                                            icon = tile.icon,
                                            onClick = tile.route?.let { r -> { onOpen(r) } },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.showWeek && state.week.isNotEmpty()) {
                item(key = "week") {
                    val title = if (state.weekOffset == 0) {
                        stringResource(R.string.today_week_this)
                    } else {
                        stringResource(R.string.today_week_of, shortDate(state.week.first().date))
                    }
                    val cells = state.week.map { day ->
                        val label = longDate(day.date)
                        WeekDayCell(
                            key = day.date.toString(),
                            weekday = day.date.toJava().dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            dayNumber = day.date.day.toString(),
                            count = day.count,
                            isToday = day.isToday,
                            isFuture = day.isFuture,
                            description = if (day.count == 0) {
                                stringResource(R.string.today_day_nothing, label)
                            } else {
                                pluralStringResource(R.plurals.today_day_description, day.count, label, day.count)
                            },
                        )
                    }
                    WeekOverview(
                        title = title,
                        days = cells,
                        onDay = { cell -> state.week.firstOrNull { it.date.toString() == cell.key }?.route?.let(onOpen) },
                        onPrevious = dash::previousWeek,
                        onNext = dash::nextWeek,
                        canGoNext = state.canGoNext,
                        previousDescription = stringResource(R.string.today_week_previous),
                        nextDescription = stringResource(R.string.today_week_next),
                    )
                }
            }
            if (hasCards.isEmpty()) {
                item(key = "empty") { NeutralEmpty(stringResource(R.string.today_empty)) }
            } else {
                items(cards, key = { it.id.value }) { module ->
                    module.DashboardCard(onOpen = { onOpen(module.startRoute) }, modifier = Modifier)
                }
            }
        }
    }
}

@Composable
private fun greetingText(g: Greeting): String = stringResource(
    when (g) {
        Greeting.MORNING -> R.string.today_greeting_morning
        Greeting.AFTERNOON -> R.string.today_greeting_afternoon
        Greeting.EVENING -> R.string.today_greeting_evening
        Greeting.NIGHT -> R.string.today_greeting_night
    },
)

private fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(year, month.ordinal + 1, day)

/** "Monday, November 9, 2026" in the user's locale. */
private fun longDate(d: LocalDate): String =
    d.toJava().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

/** "9 Nov" / "Nov 9", whichever the locale prefers. */
private fun shortDate(d: LocalDate): String {
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM")
    return d.toJava().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
