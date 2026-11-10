package app.klean.feature.calendar

import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaKind
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.feature.calendar.data.DeviceCalendar
import app.klean.feature.calendar.today.CalendarText
import app.klean.feature.calendar.today.CalendarTicker
import app.klean.feature.calendar.ui.CalendarPrefs
import app.klean.feature.calendar.ui.DeviceAccess
import app.klean.feature.calendar.ui.settings.IcsFiles
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

/** Agenda items held in memory; [items] filters by day like the real one. */
class FakeAgenda : AgendaSource {
    val all = MutableStateFlow(emptyList<AgendaItem>())
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
    override fun items(from: LocalDate, to: LocalDate): Flow<List<AgendaItem>> {
        ranges += from to to
        return all.map { l -> l.filter { it.lastDay >= from && it.firstDay <= to } }
    }
}

fun item(
    key: String,
    title: String,
    start: Long,
    end: Long = start + HOUR,
    kind: AgendaKind = AgendaKind.EVENT,
    allDay: Boolean = false,
): AgendaItem {
    val day = Instant.fromEpochMilliseconds(start).toLocalDateTime(Dubai).date
    val last = Instant.fromEpochMilliseconds(maxOf(start, end - 1)).toLocalDateTime(Dubai).date
    return AgendaItem(key, kind, title, start, end, allDay, day, last, eventId = if (kind == AgendaKind.EVENT) key else null)
}

object FakeText : CalendarText {
    override fun time(ms: Long): String = Instant.fromEpochMilliseconds(ms).toLocalDateTime(Dubai).time.toString().take(5)
    override fun nextLabel() = "Next"
    override fun nextValue(time: String, title: String) = "$time $title"
    override fun todayLabel() = "Today"
    override fun nothingElseToday() = "Nothing else today"
    override fun statusNext(title: String, time: String) = "$title $time"
    override fun allDay() = "All day"
}

object OneTick : CalendarTicker {
    override fun ticks(): Flow<Unit> = flowOf(Unit)
}

class FakePrefs : CalendarPrefs {
    override val defaultReminder = MutableStateFlow(10)
    override val showTasks = MutableStateFlow(true)
    override val showReminders = MutableStateFlow(true)
    override suspend fun setDefaultReminder(minutes: Int) { defaultReminder.value = minutes }
    override suspend fun setShowTasks(on: Boolean) { showTasks.value = on }
    override suspend fun setShowReminders(on: Boolean) { showReminders.value = on }
}

class FakeDeviceAccess(var granted: Boolean = false) : DeviceAccess {
    var cals = listOf(DeviceCalendar(7, "Work", null, "me@example.com"))
    var changes = 0
    override fun canRead() = granted
    override fun calendars() = if (granted) cals else emptyList()
    override suspend fun onAccessChanged() { changes++ }
}

class FakeIcsFiles : IcsFiles {
    var readText = ""
    var written: String? = null
    override suspend fun read(uri: String): String = readText
    override suspend fun write(uri: String, text: String) { written = text }
}
