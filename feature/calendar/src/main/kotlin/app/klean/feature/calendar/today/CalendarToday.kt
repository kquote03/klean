package app.klean.feature.calendar.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import android.content.Context
import android.text.format.DateFormat
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.feature.calendar.CalendarDayRoute
import app.klean.feature.calendar.CalendarRoute
import app.klean.feature.calendar.CalendarRoutes
import app.klean.feature.calendar.R
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.feature.calendar.data.EventChangeListener
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime

/** Copy for calendar's Today surfaces, behind an interface so they are tested without resources. */
interface CalendarText {
    /** "14:00" / "2:00 PM" in the device zone and clock style. */
    fun time(ms: Long): String
    fun nextLabel(): String
    /** "14:00 Dentist". */
    fun nextValue(time: String, title: String): String
    fun todayLabel(): String
    fun nothingElseToday(): String
    /** Status line fragment, "Dentist 14:00". */
    fun statusNext(title: String, time: String): String
    fun allDay(): String
}

class AndroidCalendarText @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val zone: ZoneProvider,
) : CalendarText {
    override fun time(ms: Long): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone.current()).time.toJavaLocalTime()
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        return t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
    override fun nextLabel() = ctx.getString(R.string.calendar_summary_next)
    override fun nextValue(time: String, title: String) = ctx.getString(R.string.calendar_summary_next_value, time, title)
    override fun todayLabel() = ctx.getString(R.string.calendar_summary_today)
    override fun nothingElseToday() = ctx.getString(R.string.calendar_summary_nothing)
    override fun statusNext(title: String, time: String) = ctx.getString(R.string.calendar_status_next, title, time)
    override fun allDay() = ctx.getString(R.string.calendar_all_day)
}

/** Emits now and then every minute, so "next" moves on while Today is open. */
fun interface CalendarTicker {
    fun ticks(): Flow<Unit>
}

class MinuteCalendarTicker @Inject constructor() : CalendarTicker {
    override fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}

private val EVENT_KINDS = setOf(AgendaKind.EVENT, AgendaKind.DEVICE)

/** Today's events (Klean's and the phone's) that haven't ended at [nowMs]; all-day ones count until midnight. */
internal fun remaining(items: List<AgendaItem>, today: LocalDate, nowMs: Long): List<AgendaItem> =
    items.filter { it.kind in EVENT_KINDS && it.isOn(today) && (it.allDay || it.end > nowMs) }

/** The next timed event today that hasn't started yet, or is still running. */
internal fun nextTimed(items: List<AgendaItem>, today: LocalDate, nowMs: Long): AgendaItem? =
    remaining(items, today, nowMs).filter { !it.allDay }.minByOrNull { it.start }

/**
 * Calendar's Today tile ("Next · 14:00 Dentist"), week activity (events per day across Klean and the phone) and
 * Today-widget rows (the rest of today's events).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarDashboardProviders(
    private val agenda: AgendaSource,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val text: CalendarText,
    private val ticker: CalendarTicker,
) : DashboardSummaryProvider, DayActivityProvider, TodayItemsProvider {
    override val moduleId: ModuleId = ModuleId.CALENDAR

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone.current()).date

    /** Today's items, following the day as it rolls over. */
    private fun todays(): Flow<Pair<LocalDate, List<AgendaItem>>> = ticker.ticks()
        .map { today() }
        .distinctUntilChanged()
        .flatMapLatest { d -> agenda.items(d, d).map { d to it } }

    override fun summaries(): Flow<List<SummaryItem>> = combine(todays(), ticker.ticks()) { (day, items), _ ->
        val now = clock.now().toEpochMilliseconds()
        val next = nextTimed(items, day, now)
        val tile = if (next != null) {
            SummaryItem(KEY_NEXT, text.nextLabel(), text.nextValue(text.time(next.start), next.title), icon = Icons.Rounded.Event, route = CalendarRoute)
        } else {
            SummaryItem(KEY_NEXT, text.todayLabel(), text.nothingElseToday(), icon = Icons.Rounded.Event, route = CalendarRoute)
        }
        listOf(tile)
    }.distinctUntilChanged()

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = agenda.items(from, to).map { items ->
        val counts = sortedMapOf<LocalDate, Int>()
        for (item in items) {
            if (item.kind !in EVENT_KINDS) continue
            var d = maxOf(item.firstDay, from)
            val last = minOf(item.lastDay, to)
            while (d <= last) {
                counts[d] = (counts[d] ?: 0) + 1
                d = d.plus(1, DateTimeUnit.DAY)
            }
        }
        counts
    }

    override fun dayRoute(date: LocalDate): Any = CalendarDayRoute(date.toString())

    override fun items(): Flow<List<TodayItem>> = combine(todays(), ticker.ticks()) { (day, items), _ ->
        remaining(items, day, clock.now().toEpochMilliseconds()).map {
            TodayItem(
                key = "calendar:${it.key}",
                timeLabel = if (it.allDay) null else text.time(it.start),
                text = it.title,
                sortKey = it.start,
                route = CalendarRoutes.dayLink(day),
            )
        }
    }.distinctUntilChanged()

    companion object {
        const val KEY_NEXT = "calendar.next"
    }
}

/**
 * Calendar's part of the holistic "Today" status line: the next event today ("Dentist 14:00"). Takes the agenda
 * and the refresh hook lazily (the repositories hold this as a listener), and reads on a tick instead of holding
 * a live Room flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CalendarStatusLine @Inject constructor(
    private val agenda: dagger.Lazy<AgendaSource>,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val text: CalendarText,
    private val ticker: CalendarTicker,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, EventChangeListener {
    override val moduleId: ModuleId = ModuleId.CALENDAR
    private val tick = MutableStateFlow(0L)

    override fun fragments(): Flow<List<StatusFragment>> =
        combine(tick, ticker.ticks()) { t, _ -> t }.mapLatest { build() }.distinctUntilChanged().catch { emit(emptyList()) }

    internal suspend fun build(): List<StatusFragment> {
        val today = clock.now().toLocalDateTime(zone.current()).date
        val next = nextTimed(agenda.get().eventSnapshot(today, today), today, clock.now().toEpochMilliseconds()) ?: return emptyList()
        return listOf(StatusFragment(KEY_NEXT, text.statusNext(next.title, text.time(next.start)), PRIORITY))
    }

    override suspend fun onEventsChanged(ids: List<String>) = refresh()

    /** Events changed: rebuild the line and re-post the holistic surfaces (notification, Today widget). */
    suspend fun refresh() {
        tick.value++
        statusRefresh.get().refresh()
    }

    companion object {
        const val KEY_NEXT = "calendar.next"
        /** Above diet's count (1); an appointment is the most useful thing to see at a glance. */
        const val PRIORITY = 5
    }
}
