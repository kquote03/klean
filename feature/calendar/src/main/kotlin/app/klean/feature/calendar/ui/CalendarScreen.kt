package app.klean.feature.calendar.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.listGroup
import app.klean.core.design.theme.Space
import app.klean.feature.calendar.R
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * The calendar, the second module of Plan (Soft cards): agenda (default), day, 3-day, week and month views of Klean's
 * events, the phone's calendars and other modules' dated items. A quiet period row (previous, the period, next) sits
 * under the tabs; the view, "Today", "New event" and settings are in the overflow menu; adding is the Add button.
 * The phone's calendars are offered once, from the overflow and from an empty agenda, never as a standing banner.
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
    val menu = buildList {
        CalendarView.entries.forEach { v -> add(MenuAction(stringResource(v.labelRes()), selected = v == state.view) { onView(v) }) }
        add(MenuAction(stringResource(R.string.calendar_today), onClick = onToday))
        add(MenuAction(stringResource(R.string.calendar_new_event), onClick = onNew))
        if (showAccess) add(MenuAction(stringResource(R.string.calendar_access_allow_menu), onClick = onAllow))
        add(MenuAction(stringResource(R.string.calendar_settings), onClick = onOpenSettings))
    }
    KleanScreen(title = stringResource(R.string.calendar_label), menu = menu) { pad ->
        val edge = pad.calculateLeftPadding(LayoutDirection.Ltr)
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            PeriodRow(title, onPrevious, onNext, Modifier.padding(horizontal = edge - Space.sm))
            val inner = PaddingValues(start = edge, end = edge, bottom = pad.calculateBottomPadding())
            when (state.view) {
                CalendarView.AGENDA -> AgendaList(state, fmt, onItem, inner, if (showAccess) MenuAction(stringResource(R.string.calendar_access_allow_menu), onClick = onAllow) else null)
                CalendarView.MONTH -> MonthGrid(state, fmt, onOpenDay, inner)
                else -> TimeGrid(state, fmt, onItem, onOpenDay)
            }
        }
    }
}

/** Previous · the period · next. The period is plain text: the view is chosen from the overflow menu. */
@Composable
private fun PeriodRow(title: String, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(Space.touchTarget)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onNext, modifier = Modifier.size(Space.touchTarget)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
        }
    }
}

internal fun CalendarView.labelRes(): Int = when (this) {
    CalendarView.AGENDA -> R.string.calendar_view_agenda
    CalendarView.DAY -> R.string.calendar_view_day
    CalendarView.THREE_DAY -> R.string.calendar_view_three_day
    CalendarView.WEEK -> R.string.calendar_view_week
    CalendarView.MONTH -> R.string.calendar_view_month
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

/** Where a day sits in the agenda: a handful of calm buckets rather than a header per day. */
internal enum class AgendaBucket { EARLIER, TODAY, THIS_WEEK, LATER }

internal fun bucketOf(day: LocalDate, today: LocalDate): AgendaBucket = when {
    day < today -> AgendaBucket.EARLIER
    day == today -> AgendaBucket.TODAY
    day <= today.plus(6, DateTimeUnit.DAY) -> AgendaBucket.THIS_WEEK
    else -> AgendaBucket.LATER
}

private fun AgendaBucket.labelRes() = when (this) {
    AgendaBucket.EARLIER -> R.string.calendar_bucket_earlier
    AgendaBucket.TODAY -> R.string.calendar_today
    AgendaBucket.THIS_WEEK -> R.string.calendar_bucket_this_week
    AgendaBucket.LATER -> R.string.calendar_bucket_later
}

@Composable
private fun AgendaList(state: CalendarUiState, fmt: CalendarFormat, onItem: (AgendaItem) -> Unit, pad: PaddingValues, offerAccess: MenuAction?) {
    if (state.loaded && state.byDay.isEmpty()) {
        EmptyState(stringResource(R.string.calendar_agenda_empty), action = offerAccess)
        return
    }
    val buckets = state.byDay.entries.groupBy { (day, _) -> bucketOf(day, state.today) }
    LazyColumn(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT), contentPadding = pad) {
        buckets.entries.forEachIndexed { i, (bucket, days) ->
            item(key = "h$bucket") { SectionHeader(stringResource(bucket.labelRes()), first = i == 0) }
            val rows = days.flatMap { (day, items) -> items.map { day to it } }
            listGroup("b$bucket", rows, { (day, it) -> "$day/${it.key}" }) { (day, item) ->
                // The start only: the range lives in the event; a long range squeezed the title on narrow phones.
                val time = if (item.allDay) stringResource(R.string.calendar_all_day) else fmt.time(item.start)
                val dated = bucket != AgendaBucket.TODAY
                ListRow(
                    title = item.title,
                    supporting = metaLabel(item),
                    meta = if (dated) "${fmt.weekdayShort(day)} $time" else time,
                    leading = { Box(Modifier.size(Space.sm).background(item.color?.let { Color(it) } ?: kindMark(item.kind), CircleShape)) },
                    onClick = { onItem(item) },
                )
            }
        }
    }
}

private val HOUR_HEIGHT: Dp = 56.dp
private val GUTTER: Dp = 52.dp

/** The solid mark beside an agenda row (the accent of [kindColor]'s container). */
@Composable
private fun kindMark(kind: AgendaKind): Color = when (kind) {
    AgendaKind.EVENT -> MaterialTheme.colorScheme.secondary
    AgendaKind.DEVICE -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.tertiary
}

/** Events in the secondary container, the phone's in primary, other modules' items in tertiary. */
@Composable
private fun kindColor(kind: AgendaKind): Pair<Color, Color> = when (kind) {
    AgendaKind.EVENT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    AgendaKind.DEVICE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

/** Day, 3-day and week: one column per day, all-day items on top, timed items on an hour grid. */
@Composable
private fun TimeGrid(state: CalendarUiState, fmt: CalendarFormat, onItem: (AgendaItem) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (HOUR_HEIGHT * 8).roundToPx() })
    val compact = state.view == CalendarView.WEEK
    Column(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER))
            state.days.forEach { d ->
                val today = d == state.today
                Column(
                    Modifier.weight(1f).defaultMinSize(minHeight = Space.touchTarget).clickable(enabled = state.days.size > 1) { onOpenDay(d) }
                        .padding(vertical = Space.xxs),
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
            Row(Modifier.fillMaxWidth().padding(bottom = Space.xxs)) {
                Text(
                    stringResource(R.string.calendar_all_day), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(GUTTER).padding(start = Space.xxs, top = Space.xxs),
                )
                allDay.forEach { list ->
                    Column(Modifier.weight(1f).padding(horizontal = Space.hairline), verticalArrangement = Arrangement.spacedBy(Space.hairline)) {
                        list.forEach { item -> Block(item, null, Modifier.fillMaxWidth().heightIn(min = Space.touchTarget), onItem, compact) }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().verticalScroll(scroll)) {
            Column(Modifier.width(GUTTER)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HOUR_HEIGHT)) {
                        if (h > 0) Text(fmt.hour(h), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.xs))
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
                        HorizontalDivider(Modifier.offset(y = HOUR_HEIGHT * h), color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                    val laneWidth = maxWidth / laneCount
                    timed.forEach { item ->
                        val startMin = ((item.start - dayStart) / 60_000).coerceIn(0, 24 * 60 - 1)
                        val endMin = ((item.end - dayStart) / 60_000).coerceIn(startMin + 1, 24L * 60)
                        val top = HOUR_HEIGHT * (startMin / 60f)
                        val height = maxOf(HOUR_HEIGHT * ((endMin - startMin) / 60f), Space.touchTarget)
                        val lane = lanes[item.key] ?: 0
                        Block(
                            item, fmt.time(item.start),
                            Modifier.offset(x = laneWidth * lane, y = top).width(laneWidth).height(height).padding(Space.hairline),
                            onItem,
                            compact,
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
private fun Block(item: AgendaItem, time: String?, modifier: Modifier, onItem: (AgendaItem) -> Unit, compact: Boolean = false) {
    val (bg, fg) = kindColor(item.kind)
    Surface(
        color = bg, contentColor = fg, shape = MaterialTheme.shapes.small,
        modifier = modifier.clickable { onItem(item) },
    ) {
        Column(Modifier.padding(horizontal = Space.xxs, vertical = Space.hairline)) {
            // Week columns are narrow: one ellipsized line, never a word broken across lines, and no time line.
            Text(
                item.title, style = MaterialTheme.typography.labelMedium,
                maxLines = if (compact) 1 else 2, softWrap = !compact, overflow = TextOverflow.Ellipsis,
            )
            if (time != null && !compact) Text(time, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Month: whole weeks from Monday, a dot per item (up to three), today ringed. Tap a day for its day view. */
@Composable
private fun MonthGrid(state: CalendarUiState, fmt: CalendarFormat, onOpenDay: (LocalDate) -> Unit, pad: PaddingValues) {
    val weeks = state.days.chunked(7)
    Column(Modifier.fillMaxSize().testTag(CLUTTER_EXEMPT).verticalScroll(rememberScrollState()).padding(pad)) {
        Row(Modifier.fillMaxWidth()) {
            weeks.firstOrNull()?.forEach { d ->
                Text(
                    fmt.weekdayShort(d), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = Space.xs), textAlign = TextAlign.Center,
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
                            .padding(vertical = Space.xxs),
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
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.hairline * 3), modifier = Modifier.padding(top = Space.hairline)) {
                            items.take(3).forEach { Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)) }
                        }
                    }
                }
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                val day = if (item.firstDay == item.lastDay) fmt.shortDay(item.firstDay) else fmt.range(item.firstDay, item.lastDay)
                Text("$day · ${timeLabel(item, fmt)}")
                metaLabel(item)?.let { Text(it) }
                Text(stringResource(R.string.calendar_read_only), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.calendar_close)) } },
        dismissButton = onOpen?.let { open -> { TextButton(onClick = open) { Text(stringResource(R.string.calendar_open)) } } },
    )
}
