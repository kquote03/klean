package app.klean.feature.calendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.calendar.R
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * The calendar: agenda (default), day, 3-day, week and month views of Klean's events, the phone's calendars, and
 * other modules' dated items. Asks for the calendar permission just in time, from a card that can be put off.
 */
@Composable
fun CalendarScreen(
    onOpenEvent: (eventId: String, occurrenceStart: Long) -> Unit,
    onNewEvent: (LocalDate?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onAccessResult() }
    LifecycleResumeEffect(Unit) {
        vm.recheckAccess()
        onPauseOrDispose { }
    }
    var later by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AgendaItem?>(null) }
    CalendarScreenContent(
        state = state,
        showAccess = !state.canReadDevice && !later,
        onAllow = { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        onLater = { later = true },
        onView = vm::setView,
        onPrevious = vm::previous,
        onNext = vm::next,
        onToday = vm::goToday,
        onOpenDay = vm::openDay,
        onItem = { item ->
            val id = item.eventId
            if (item.kind == AgendaKind.EVENT && id != null) onOpenEvent(id, item.occurrenceStart ?: -1L) else detail = item
        },
        onNew = { onNewEvent(if (state.view == CalendarView.DAY) state.anchor else null) },
        onOpenSettings = onOpenSettings,
    )
    detail?.let { item ->
        ReadOnlyItemDialog(
            item = item,
            onDismiss = { detail = null },
            onOpen = item.route?.let { link -> { detail = null; onOpenLink(link) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreenContent(
    state: CalendarUiState,
    showAccess: Boolean,
    onAllow: () -> Unit,
    onLater: () -> Unit,
    onView: (CalendarView) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onItem: (AgendaItem) -> Unit,
    onNew: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val fmt = rememberCalendarFormat()
    val title = when (state.view) {
        CalendarView.AGENDA, CalendarView.MONTH -> fmt.month(state.anchor)
        CalendarView.DAY -> fmt.longDay(state.anchor)
        CalendarView.THREE_DAY, CalendarView.WEEK -> state.days.let { if (it.isEmpty()) "" else fmt.range(it.first(), it.last()) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = onToday, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.calendar_today))
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.calendar_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            val newLabel = stringResource(R.string.calendar_new_event)
            ExtendedFloatingActionButton(
                onClick = onNew,
                modifier = Modifier.semantics { contentDescription = newLabel },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.calendar_new_event)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous))
                }
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalendarView.entries.forEach { v ->
                        FilterChip(
                            selected = state.view == v,
                            onClick = { onView(v) },
                            label = { Text(stringResource(v.labelRes())) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
                }
            }
            if (showAccess) AccessCard(onAllow, onLater)
            when (state.view) {
                CalendarView.AGENDA -> AgendaList(state, fmt, onItem)
                CalendarView.MONTH -> MonthGrid(state, fmt, onOpenDay)
                else -> TimeGrid(state, fmt, onItem, onOpenDay)
            }
        }
    }
}

private fun CalendarView.labelRes(): Int = when (this) {
    CalendarView.AGENDA -> R.string.calendar_view_agenda
    CalendarView.DAY -> R.string.calendar_view_day
    CalendarView.THREE_DAY -> R.string.calendar_view_three_day
    CalendarView.WEEK -> R.string.calendar_view_week
    CalendarView.MONTH -> R.string.calendar_view_month
}

@Composable
private fun AccessCard(onAllow: () -> Unit, onLater: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.calendar_access_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.calendar_access_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.calendar_access_later)) }
                TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.calendar_access_allow)) }
            }
        }
    }
}

/** "All day", "14:00" or "14:00 – 15:00"; reminders and tasks show their time only. */
@Composable
internal fun timeLabel(item: AgendaItem, fmt: CalendarFormat, stacked: Boolean = false): String = when {
    item.allDay -> stringResource(R.string.calendar_all_day)
    // In a list's time column, start and end sit on two lines instead of breaking mid-range.
    item.end > item.start && stacked -> stringResource(R.string.calendar_time_range_stacked, fmt.time(item.start), fmt.time(item.end))
    item.end > item.start -> stringResource(R.string.calendar_time_range, fmt.time(item.start), fmt.time(item.end))
    else -> fmt.time(item.start)
}

@Composable
internal fun metaLabel(item: AgendaItem): String? {
    val kind = when (item.kind) {
        AgendaKind.TASK -> stringResource(R.string.calendar_kind_task)
        AgendaKind.REMINDER -> stringResource(R.string.calendar_kind_reminder)
        AgendaKind.BIRTHDAY -> stringResource(R.string.calendar_kind_birthday)
        else -> null
    }
    val source = item.sourceName?.takeIf { item.kind == AgendaKind.DEVICE }?.let { stringResource(R.string.calendar_from_source, it) }
    return listOfNotNull(kind, source, item.location).joinToString(" · ").ifEmpty { null }
}

@Composable
private fun AgendaList(state: CalendarUiState, fmt: CalendarFormat, onItem: (AgendaItem) -> Unit) {
    if (state.loaded && state.byDay.isEmpty()) {
        NeutralEmpty(stringResource(R.string.calendar_agenda_empty), Modifier.padding(16.dp))
        return
    }
    val tomorrow = state.today.plus(1, DateTimeUnit.DAY)
    LazyColumn(Modifier.fillMaxSize()) {
        state.byDay.forEach { (day, items) ->
            item(key = "h$day") {
                val label = when (day) {
                    state.today -> stringResource(R.string.calendar_day_today, fmt.shortDay(day))
                    tomorrow -> stringResource(R.string.calendar_day_tomorrow, fmt.shortDay(day))
                    else -> fmt.shortDay(day)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(items, key = { "$day/${it.key}" }) { item ->
                val meta = metaLabel(item)
                EntryRow(
                    time = timeLabel(item, fmt, stacked = true),
                    text = item.title,
                    meta = meta?.let { m -> { Text(m, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    onClick = { onItem(item) },
                    trailing = item.color?.let { c -> { Box(Modifier.size(10.dp).background(Color(c), CircleShape)) } },
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

private val HOUR_HEIGHT: Dp = 56.dp
private val GUTTER: Dp = 52.dp

@Composable
private fun kindColor(kind: AgendaKind): Pair<Color, Color> = when (kind) {
    AgendaKind.EVENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    AgendaKind.DEVICE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** Day, 3-day and week: one column per day, all-day items on top, timed items on an hour grid. */
@Composable
private fun TimeGrid(state: CalendarUiState, fmt: CalendarFormat, onItem: (AgendaItem) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (HOUR_HEIGHT * 8).roundToPx() })
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER))
            state.days.forEach { d ->
                val today = d == state.today
                Column(
                    Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).clickable(enabled = state.days.size > 1) { onOpenDay(d) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(fmt.weekdayShort(d), style = MaterialTheme.typography.labelMedium)
                    Text(
                        fmt.dayNumber(d),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                        color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val allDay = state.days.map { d -> state.byDay[d].orEmpty().filter { it.allDay } }
        if (allDay.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    stringResource(R.string.calendar_all_day), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(GUTTER).padding(start = 4.dp, top = 4.dp),
                )
                allDay.forEach { list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        list.forEach { item -> Block(item, null, Modifier.fillMaxWidth().heightIn(min = 48.dp), onItem) }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().verticalScroll(scroll)) {
            Column(Modifier.width(GUTTER)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HOUR_HEIGHT)) {
                        if (h > 0) Text(fmt.hour(h), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            state.days.forEach { d ->
                val dayStart = d.atStartOfDayIn(zone).toEpochMilliseconds()
                val timed = state.byDay[d].orEmpty().filter { !it.allDay }
                val lanes = lanes(timed, dayStart)
                val laneCount = (lanes.values.maxOrNull() ?: 0) + 1
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(HOUR_HEIGHT * 24)) {
                    for (h in 1 until 24) {
                        HorizontalDivider(Modifier.offset(y = HOUR_HEIGHT * h), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    val laneWidth = maxWidth / laneCount
                    timed.forEach { item ->
                        val startMin = ((item.start - dayStart) / 60_000).coerceIn(0, 24 * 60 - 1)
                        val endMin = ((item.end - dayStart) / 60_000).coerceIn(startMin + 1, 24L * 60)
                        val top = HOUR_HEIGHT * (startMin / 60f)
                        val height = maxOf(HOUR_HEIGHT * ((endMin - startMin) / 60f), 48.dp)
                        val lane = lanes[item.key] ?: 0
                        Block(
                            item, fmt.time(item.start),
                            Modifier.offset(x = laneWidth * lane, y = top).width(laneWidth).height(height).padding(1.dp),
                            onItem,
                        )
                    }
                }
            }
        }
    }
}

/** Side-by-side lanes for overlapping items of one day (greedy, in start order). */
private fun lanes(items: List<AgendaItem>, dayStart: Long): Map<String, Int> {
    val ends = mutableListOf<Long>()
    val out = HashMap<String, Int>()
    for (item in items.sortedBy { it.start }) {
        val start = maxOf(item.start, dayStart)
        // A short item still takes 45 minutes of room, as drawn.
        val end = maxOf(item.end, start + 45 * 60_000L)
        val lane = ends.indexOfFirst { it <= start }.takeIf { it >= 0 } ?: ends.size.also { ends += 0L }
        ends[lane] = end
        out[item.key] = lane
    }
    return out
}

@Composable
private fun Block(item: AgendaItem, time: String?, modifier: Modifier, onItem: (AgendaItem) -> Unit) {
    val (bg, fg) = kindColor(item.kind)
    Surface(
        color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable { onItem(item) },
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (time != null) Text(time, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Month: whole weeks from Monday, a dot per item (up to three), today ringed. Tap a day for its day view. */
@Composable
private fun MonthGrid(state: CalendarUiState, fmt: CalendarFormat, onOpenDay: (LocalDate) -> Unit) {
    val weeks = state.days.chunked(7)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weeks.firstOrNull()?.forEach { d ->
                Text(
                    fmt.weekdayShort(d), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val items = state.byDay[d].orEmpty()
                    val inMonth = d.month == state.anchor.month
                    val today = d == state.today
                    val label = pluralStringResource(R.plurals.calendar_month_cell, items.size, fmt.shortDay(d), items.size)
                    Column(
                        Modifier.weight(1f).heightIn(min = 56.dp).clickable { onOpenDay(d) }.semantics { contentDescription = label }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(32.dp).then(
                                if (today) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                fmt.dayNumber(d),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    today -> MaterialTheme.colorScheme.onPrimaryContainer
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                            items.take(3).forEach { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(88.dp))
    }
}

/** A phone event, task, reminder or birthday: shown, not edited here. */
@Composable
private fun ReadOnlyItemDialog(item: AgendaItem, onDismiss: () -> Unit, onOpen: (() -> Unit)?) {
    val fmt = rememberCalendarFormat()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val day = if (item.firstDay == item.lastDay) fmt.shortDay(item.firstDay) else fmt.range(item.firstDay, item.lastDay)
                Text("$day · ${timeLabel(item, fmt)}")
                metaLabel(item)?.let { Text(it) }
                Text(stringResource(R.string.calendar_read_only), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) } },
        dismissButton = onOpen?.let { open -> { TextButton(onClick = open) { Text(stringResource(R.string.calendar_open)) } } },
    )
}
