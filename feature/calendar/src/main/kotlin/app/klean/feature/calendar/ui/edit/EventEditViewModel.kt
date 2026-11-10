package app.klean.feature.calendar.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.calendar.data.Repeat
import app.klean.feature.calendar.ui.CalendarPrefs
import app.klean.shared.time.atMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** The editor's fields. Times are local to the event's zone; [reminder] is minutes before, -1 = none. */
data class EventForm(
    val title: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startMinutes: Int,
    val endDate: LocalDate,
    val endMinutes: Int,
    val location: String = "",
    val notes: String = "",
    val repeat: Repeat = Repeat.NONE,
    /** The rule as stored, kept when [repeat] is CUSTOM (an imported or hand-written RRULE). */
    val rrule: String? = null,
    val reminder: Int = -1,
)

data class EventEditUiState(
    val form: EventForm,
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val recurring: Boolean = false,
    val done: Boolean = false,
) {
    val canSave: Boolean get() = form.title.isNotBlank()
}

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CalendarRepository,
    private val prefs: CalendarPrefs,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val eventId: String? = savedState.get<String>("eventId")
    private val occurrenceStart: Long? = savedState.get<Long>("occurrenceStart")?.takeIf { it >= 0 }
    private var eventZone: TimeZone = zone.current()
    private var lastTimedReminder = -1

    private val _state = MutableStateFlow(EventEditUiState(newForm(savedState.get<String>("isoDate"))))
    val state: StateFlow<EventEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (eventId == null) {
                val r = prefs.defaultReminder.first()
                lastTimedReminder = r
                _state.update { it.copy(form = it.form.copy(reminder = r), loaded = true) }
            } else {
                val e = repo.get(eventId)?.takeUnless { it.sync.deleted }
                if (e == null) {
                    _state.update { it.copy(done = true) }
                    return@launch
                }
                eventZone = runCatching { TimeZone.of(e.zone) }.getOrDefault(zone.current())
                val start = Instant.fromEpochMilliseconds(e.startAt).toLocalDateTime(eventZone)
                val end = Instant.fromEpochMilliseconds(e.endAt).toLocalDateTime(eventZone)
                val endDate = if (e.allDay) maxOf(start.date, end.date.minus(1, DateTimeUnit.DAY)) else end.date
                lastTimedReminder = e.reminderMinutes ?: -1
                _state.value = EventEditUiState(
                    form = EventForm(
                        title = e.title, allDay = e.allDay,
                        startDate = start.date, startMinutes = start.hour * 60 + start.minute,
                        endDate = endDate, endMinutes = end.hour * 60 + end.minute,
                        location = e.location.orEmpty(), notes = e.notes.orEmpty(),
                        repeat = Repeat.of(e.rrule), rrule = e.rrule, reminder = e.reminderMinutes ?: -1,
                    ),
                    isNew = false, loaded = true, recurring = e.rrule != null,
                )
            }
        }
    }

    private fun newForm(isoDate: String?): EventForm {
        val now = clock.now().toLocalDateTime(zone.current())
        val asked = isoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // Today: the next whole hour. Another day: 09:00.
        val (date, minutes) = if (asked == null || asked == now.date) {
            val m = (now.hour + 1) * 60
            if (m >= 24 * 60) now.date.plus(1, DateTimeUnit.DAY) to 9 * 60 else now.date to m
        } else {
            asked to 9 * 60
        }
        val end = minutes + 60
        return if (end >= 24 * 60) {
            EventForm(startDate = date, startMinutes = minutes, endDate = date.plus(1, DateTimeUnit.DAY), endMinutes = end - 24 * 60)
        } else {
            EventForm(startDate = date, startMinutes = minutes, endDate = date, endMinutes = end)
        }
    }

    private fun edit(f: (EventForm) -> EventForm) = _state.update { it.copy(form = f(it.form)) }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setLocation(v: String) = edit { it.copy(location = v) }
    fun setNotes(v: String) = edit { it.copy(notes = v) }
    fun setRepeat(r: Repeat) = edit { it.copy(repeat = r, rrule = if (r == Repeat.CUSTOM) it.rrule else r.rrule) }
    fun setReminder(minutes: Int) = edit {
        if (!it.allDay) lastTimedReminder = minutes
        it.copy(reminder = minutes)
    }

    fun setAllDay(on: Boolean) = edit {
        if (on) it.copy(allDay = true, reminder = -1) else it.copy(allDay = false, reminder = lastTimedReminder)
    }

    /** Moving the start keeps the length; the end follows. */
    fun setStartDate(d: LocalDate) = edit { f ->
        val shift = f.startDate.daysUntil(d)
        f.copy(startDate = d, endDate = f.endDate.plus(shift, DateTimeUnit.DAY))
    }

    fun setStartTime(minutes: Int) = edit { f ->
        val length = f.lengthMinutes()
        val end = f.startDate.atMinutes(minutes + length)
        f.copy(startMinutes = minutes, endDate = end.first, endMinutes = end.second)
    }

    /** The end never goes before the start. */
    fun setEndDate(d: LocalDate) = edit { f ->
        val date = maxOf(d, f.startDate)
        val minutes = if (date == f.startDate && !f.allDay && f.endMinutes < f.startMinutes) f.startMinutes + 60 else f.endMinutes
        f.copy(endDate = date, endMinutes = minutes.coerceAtMost(24 * 60 - 1))
    }

    fun setEndTime(minutes: Int) = edit { f ->
        if (f.endDate == f.startDate && minutes < f.startMinutes) {
            f.copy(endDate = f.startDate.plus(1, DateTimeUnit.DAY), endMinutes = minutes)
        } else {
            f.copy(endMinutes = minutes)
        }
    }

    private fun EventForm.lengthMinutes(): Int = startDate.daysUntil(endDate) * 24 * 60 + endMinutes - startMinutes

    private fun LocalDate.atMinutes(total: Int): Pair<LocalDate, Int> = plus(total / (24 * 60), DateTimeUnit.DAY) to total % (24 * 60)

    private fun EventForm.toDraft(): EventDraft {
        val start = if (allDay) startDate.atStartOfDayIn(eventZone).toEpochMilliseconds() else atMinute(startDate, startMinutes, eventZone)
        val end = if (allDay) {
            endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(eventZone).toEpochMilliseconds()
        } else {
            maxOf(start, atMinute(endDate, endMinutes, eventZone))
        }
        return EventDraft(
            title = title, startAt = start, endAt = end, allDay = allDay, zone = eventZone.id,
            location = location, notes = notes, rrule = if (repeat == Repeat.CUSTOM) rrule else repeat.rrule,
            reminderMinutes = reminder.takeIf { it >= 0 },
        )
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val draft = s.form.toDraft()
            if (eventId == null) repo.add(draft) else repo.update(eventId, draft)
            _state.update { it.copy(done = true) }
        }
    }

    fun deleteAll() {
        val id = eventId ?: return
        viewModelScope.launch {
            repo.delete(id)
            _state.update { it.copy(done = true) }
        }
    }

    /** Leaves out only the occurrence that was tapped (recurring events). */
    fun deleteThis() {
        val id = eventId ?: return
        val at = occurrenceStart ?: return deleteAll()
        viewModelScope.launch {
            repo.skipOccurrence(id, at)
            _state.update { it.copy(done = true) }
        }
    }
}
