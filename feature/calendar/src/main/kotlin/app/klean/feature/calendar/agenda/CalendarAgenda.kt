package app.klean.feature.calendar.agenda

import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.Exdates
import app.klean.feature.calendar.data.RecurrenceExpander
import app.klean.feature.calendar.data.SourceKind
import app.klean.feature.calendar.device.DeviceInstance
import app.klean.feature.calendar.device.DeviceInstances
import app.klean.feature.calendar.reminder.EventReminderSync
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class AgendaKind { EVENT, DEVICE, BIRTHDAY, TASK, REMINDER, OTHER }

/**
 * One thing on the calendar on one or more days. All-day items cover [firstDay]..[lastDay]; timed ones run from
 * [start] to [end] (UTC ms) and their days are read in the device zone.
 */
data class AgendaItem(
    val key: String,
    val kind: AgendaKind,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val sourceName: String? = null,
    val color: Int? = null,
    /** Klean event id (EVENT only). */
    val eventId: String? = null,
    val recurring: Boolean = false,
    /** The occurrence's real start (EVENT only), for "delete only this one". */
    val occurrenceStart: Long? = null,
    val location: String? = null,
    val notes: String? = null,
    /** Deep link for items from other modules. */
    val route: String? = null,
) {
    /** Only Klean's own events are edited here; the rest belongs to the phone or another module. */
    val readOnly: Boolean get() = kind != AgendaKind.EVENT

    fun isOn(day: LocalDate): Boolean = day in firstDay..lastDay
}

/** Day order: all-day first (by title), then by start time. */
val AgendaOrder: Comparator<AgendaItem> =
    compareBy<AgendaItem>({ it.firstDay }, { !it.allDay }, { if (it.allDay) 0L else it.start }, { it.title.lowercase() })

/** [items] listed under every day of [from]..[to] they are on, in day order; empty days are absent. */
fun byDay(items: List<AgendaItem>, from: LocalDate, to: LocalDate): Map<LocalDate, List<AgendaItem>> {
    val out = sortedMapOf<LocalDate, MutableList<AgendaItem>>()
    for (item in items) {
        var d = maxOf(item.firstDay, from)
        val last = minOf(item.lastDay, to)
        while (d <= last) {
            out.getOrPut(d) { mutableListOf() } += item
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return out.mapValues { (_, l) -> l.sortedWith(compareBy<AgendaItem>({ !it.allDay }, { if (it.allDay) 0L else it.start }, { it.title.lowercase() })) }
}

/** What the calendar shows for a range of days. An interface so screens and Today are tested with fakes. */
interface AgendaSource {
    fun items(from: LocalDate, to: LocalDate): Flow<List<AgendaItem>>
    /**
     * Events only (Klean's and the phone's), read once without live database flows: for the status line, which
     * also runs where no Room flow may be held open.
     */
    suspend fun eventSnapshot(from: LocalDate, to: LocalDate): List<AgendaItem> =
        items(from, to).first().filter { it.kind == AgendaKind.EVENT || it.kind == AgendaKind.DEVICE }
}

/**
 * Merges Klean's events (recurrences expanded), the phone's calendars (read-only), and other modules' dated items
 * ([DayItemsProvider]: birthdays, task due dates, reminders), honouring calendar selection, the module gate and the
 * "show tasks / reminders" settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarAgenda(
    private val repo: CalendarRepository,
    private val device: DeviceInstances,
    private val providers: Set<DayItemsProvider>,
    private val gate: ModuleGate,
    private val showTasks: () -> Flow<Boolean>,
    private val showReminders: () -> Flow<Boolean>,
    private val zone: ZoneProvider,
) : AgendaSource {

    override fun items(from: LocalDate, to: LocalDate): Flow<List<AgendaItem>> {
        val tz = zone.current()
        val fromMs = from.atStartOfDayIn(tz).toEpochMilliseconds()
        val toMs = to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val klean = combine(repo.observeEvents(), repo.observeSources()) { events, sources -> events to sources }
        // A day of padding either side: phone all-day rows are UTC midnights.
        val phone = device.instances(fromMs - PAD_MS, toMs + PAD_MS)
        val others = gate.disabledIds.distinctUntilChanged().flatMapLatest { off ->
            val on = providers.filter { it.moduleId !in off }.sortedBy { it.moduleId.value }
            if (on.isEmpty()) flowOf(emptyList()) else combine(on.map { it.items(from, to) }) { l -> l.flatMap { it } }
        }
        return combine(klean, phone, others, showTasks(), showReminders()) { (events, sources), deviceRows, dayItems, tasksOn, remindersOn ->
            val hiddenSources = sources.filter { !it.selected }.map { it.id }.toSet()
            buildList {
                addAll(kleanItems(events, sources, fromMs, toMs, tz))
                for (row in deviceRows) {
                    if (device.sourceId(row.calendarId) in hiddenSources) continue
                    add(deviceItem(row, tz))
                }
                for (d in dayItems) {
                    if (d.kind == DayItemKind.TASK && !tasksOn) continue
                    if (d.kind == DayItemKind.REMINDER && !remindersOn) continue
                    add(dayItem(d, tz))
                }
            }.filter { it.lastDay >= from && it.firstDay <= to }.sortedWith(AgendaOrder)
        }
    }

    override suspend fun eventSnapshot(from: LocalDate, to: LocalDate): List<AgendaItem> {
        val tz = zone.current()
        val fromMs = from.atStartOfDayIn(tz).toEpochMilliseconds()
        val toMs = to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
        val sources = repo.sources()
        val hiddenSources = sources.filter { !it.selected }.map { it.id }.toSet()
        val deviceRows = device.instances(fromMs - PAD_MS, toMs + PAD_MS).first()
        return buildList {
            addAll(kleanItems(repo.liveEvents(), sources, fromMs, toMs, tz))
            for (row in deviceRows) if (device.sourceId(row.calendarId) !in hiddenSources) add(deviceItem(row, tz))
        }.filter { it.lastDay >= from && it.firstDay <= to }.sortedWith(AgendaOrder)
    }

    private fun kleanItems(events: List<EventEntity>, sources: List<CalendarSourceEntity>, fromMs: Long, toMs: Long, tz: TimeZone): List<AgendaItem> {
        val kleanHidden = sources.firstOrNull { it.kind == SourceKind.KLEAN }?.selected == false
        val byId = sources.associateBy { it.id }
        return events.flatMap { e ->
            val src = e.calendarId?.let(byId::get)
            val hidden = if (e.calendarId == null) kleanHidden else src?.selected == false
            if (hidden || e.sync.deleted) return@flatMap emptyList()
            val eventZone = runCatching { TimeZone.of(e.zone) }.getOrDefault(tz)
            val duration = (e.endAt - e.startAt).coerceAtLeast(0)
            RecurrenceExpander.occurrences(
                e.startAt, duration, e.rrule, Exdates.decode(e.exdates).toSet(), eventZone, fromMs - PAD_MS, toMs + PAD_MS,
            ).map { start ->
                val end = start + duration
                val (first, last) = if (e.allDay) {
                    start.dateIn(eventZone) to maxOf(start, end - 1).dateIn(eventZone)
                } else {
                    start.dateIn(tz) to maxOf(start, end - 1).dateIn(tz)
                }
                AgendaItem(
                    key = "event:${e.id}:$start", kind = AgendaKind.EVENT, title = e.title,
                    start = if (e.allDay) first.atStartOfDayIn(tz).toEpochMilliseconds() else start,
                    end = if (e.allDay) last.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() else end,
                    allDay = e.allDay, firstDay = first, lastDay = last, sourceName = src?.name, color = src?.color,
                    eventId = e.id, recurring = e.rrule != null, occurrenceStart = start, location = e.location, notes = e.notes,
                )
            }
        }
    }

    private fun deviceItem(row: DeviceInstance, tz: TimeZone): AgendaItem {
        val (first, last) = if (row.allDay) {
            row.begin.dateIn(TimeZone.UTC) to maxOf(row.begin, row.end - 1).dateIn(TimeZone.UTC)
        } else {
            row.begin.dateIn(tz) to maxOf(row.begin, row.end - 1).dateIn(tz)
        }
        return AgendaItem(
            key = "device:${row.eventId}:${row.begin}", kind = AgendaKind.DEVICE, title = row.title,
            start = if (row.allDay) first.atStartOfDayIn(tz).toEpochMilliseconds() else row.begin,
            end = if (row.allDay) last.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() else row.end,
            allDay = row.allDay, firstDay = first, lastDay = last, sourceName = row.calendarName, color = row.color,
            location = row.location,
        )
    }

    private fun dayItem(d: DayItem, tz: TimeZone): AgendaItem {
        val start = if (d.minutes != null) atMinute(d.date, d.minutes, tz) else d.date.atStartOfDayIn(tz).toEpochMilliseconds()
        val kind = when (d.kind) {
            DayItemKind.BIRTHDAY -> AgendaKind.BIRTHDAY
            DayItemKind.TASK -> AgendaKind.TASK
            DayItemKind.REMINDER -> AgendaKind.REMINDER
            DayItemKind.OTHER -> AgendaKind.OTHER
        }
        return AgendaItem(
            key = d.key, kind = kind, title = d.title, start = start,
            end = if (d.minutes != null) start else d.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds(),
            allDay = d.minutes == null, firstDay = d.date, lastDay = d.date, route = d.route,
        )
    }

    companion object {
        private const val PAD_MS = 86_400_000L
    }
}

private fun Long.dateIn(tz: TimeZone): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

/** Tasks with a due date in the range (read-only; the Tasks module owns them). Done tasks are left out. */
class TaskDueItems @Inject constructor(private val db: KleanDatabase) : DayItemsProvider {
    override val moduleId: ModuleId = ModuleId.TASKS
    override fun items(from: LocalDate, to: LocalDate): Flow<List<DayItem>> =
        db.taskDao().observeDue(from.toString(), to.toString()).map { tasks ->
            tasks.filter { it.completedAt == null && it.status != "DONE" && it.dueDate != null }.mapNotNull { t ->
                val date = runCatching { LocalDate.parse(t.dueDate!!) }.getOrNull() ?: return@mapNotNull null
                DayItem("task:${t.id}", date, t.title, t.dueMinutes, DayItemKind.TASK)
            }
        }
}

/**
 * Reminders in the range (read-only; the Reminders module owns them), without the ones Calendar made for its own
 * events (those events are already shown) and without dismissed ones.
 */
class ReminderDayItems @Inject constructor(
    private val db: KleanDatabase,
    private val repo: CalendarRepository,
    private val zone: ZoneProvider,
) : DayItemsProvider {
    override val moduleId: ModuleId = ModuleId.REMINDERS
    override fun items(from: LocalDate, to: LocalDate): Flow<List<DayItem>> =
        combine(db.reminderDao().observeAll(), repo.observeEvents()) { reminders, events ->
            val tz = zone.current()
            val ours = events.map { EventReminderSync.reminderId(it.id) }.toSet()
            reminders.filter { it.id !in ours && it.state != DefaultReminderRepository.DISMISSED }.mapNotNull { r ->
                val at = Instant.fromEpochMilliseconds(r.snoozedUntil ?: r.fireAt).toLocalDateTime(tz)
                if (at.date < from || at.date > to) return@mapNotNull null
                DayItem("reminder:${r.id}", at.date, r.title, at.hour * 60 + at.minute, DayItemKind.REMINDER)
            }
        }
}
