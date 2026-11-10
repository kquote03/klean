package app.klean.feature.calendar.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.calendar.CalendarKeys
import app.klean.feature.calendar.agenda.AgendaItem
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.feature.calendar.agenda.byDay
import app.klean.feature.calendar.data.DeviceCalendar
import app.klean.feature.calendar.device.DeviceCalendarSource
import app.klean.feature.calendar.mirror.MirrorHook
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class CalendarView { AGENDA, DAY, THREE_DAY, WEEK, MONTH }

object CalendarRange {
    const val AGENDA_DAYS = 14

    /** The days [view] shows around [anchor], both inclusive. Weeks start on Monday. */
    fun of(view: CalendarView, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (view) {
        CalendarView.AGENDA -> anchor to anchor.plus(AGENDA_DAYS - 1, DateTimeUnit.DAY)
        CalendarView.DAY -> anchor to anchor
        CalendarView.THREE_DAY -> anchor to anchor.plus(2, DateTimeUnit.DAY)
        CalendarView.WEEK -> weekStart(anchor).let { it to it.plus(6, DateTimeUnit.DAY) }
        CalendarView.MONTH -> {
            val first = LocalDate(anchor.year, anchor.month.number, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            weekStart(first) to weekStart(last).plus(6, DateTimeUnit.DAY)
        }
    }

    fun weekStart(d: LocalDate): LocalDate = d.minus(d.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber, DateTimeUnit.DAY)

    /** Where next/previous go from [anchor]. */
    fun step(view: CalendarView, anchor: LocalDate, forward: Boolean): LocalDate {
        val sign = if (forward) 1 else -1
        return when (view) {
            CalendarView.AGENDA -> anchor.plus(sign * AGENDA_DAYS, DateTimeUnit.DAY)
            CalendarView.DAY -> anchor.plus(sign, DateTimeUnit.DAY)
            CalendarView.THREE_DAY -> anchor.plus(sign * 3, DateTimeUnit.DAY)
            CalendarView.WEEK -> anchor.plus(sign * 7, DateTimeUnit.DAY)
            CalendarView.MONTH -> anchor.plus(sign, DateTimeUnit.MONTH)
        }
    }
}

/** Synced calendar settings, behind an interface so view models are tested without Room. */
interface CalendarPrefs {
    val defaultReminder: Flow<Int>
    val showTasks: Flow<Boolean>
    val showReminders: Flow<Boolean>
    suspend fun setDefaultReminder(minutes: Int)
    suspend fun setShowTasks(on: Boolean)
    suspend fun setShowReminders(on: Boolean)
}

class SettingsCalendarPrefs @Inject constructor(private val settings: SettingsRepository) : CalendarPrefs {
    override val defaultReminder: Flow<Int> = settings.observe(CalendarKeys.DefaultReminderMinutes)
    override val showTasks: Flow<Boolean> = settings.observe(CalendarKeys.ShowTasks)
    override val showReminders: Flow<Boolean> = settings.observe(CalendarKeys.ShowReminders)
    override suspend fun setDefaultReminder(minutes: Int) = settings.set(CalendarKeys.DefaultReminderMinutes, minutes)
    override suspend fun setShowTasks(on: Boolean) = settings.set(CalendarKeys.ShowTasks, on)
    override suspend fun setShowReminders(on: Boolean) = settings.set(CalendarKeys.ShowReminders, on)
}

/** The phone's calendars: the just-in-time permission and what follows a grant. */
interface DeviceAccess {
    fun canRead(): Boolean
    fun calendars(): List<DeviceCalendar>
    /** The permission was answered: read again, list the calendars, and mirror Klean's events if now allowed. */
    suspend fun onAccessChanged()
}

class CalendarDeviceAccess @Inject constructor(
    private val device: DeviceCalendarSource,
    private val mirror: MirrorHook,
) : DeviceAccess {
    override fun canRead() = device.canRead()
    override fun calendars() = device.calendars()
    override suspend fun onAccessChanged() {
        device.refresh()
        device.syncSources()
        mirror.reschedule()
    }
}

data class CalendarUiState(
    val view: CalendarView = CalendarView.AGENDA,
    val anchor: LocalDate,
    val today: LocalDate,
    val days: List<LocalDate> = emptyList(),
    val items: List<AgendaItem> = emptyList(),
    /** Items under each day they are on, days without items absent. */
    val byDay: Map<LocalDate, List<AgendaItem>> = emptyMap(),
    val canReadDevice: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val agenda: AgendaSource,
    private val device: DeviceAccess,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone.current()).date
    private val initialDay: LocalDate? = savedState.get<String>("isoDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private data class Position(val view: CalendarView, val anchor: LocalDate)

    private val position = MutableStateFlow(
        Position(if (initialDay != null) CalendarView.DAY else CalendarView.AGENDA, initialDay ?: today),
    )
    private val access = MutableStateFlow(device.canRead())

    val state: StateFlow<CalendarUiState> = combine(position, access) { p, a -> p to a }
        .flatMapLatest { (p, canRead) ->
            val (from, to) = CalendarRange.of(p.view, p.anchor)
            agenda.items(from, to).map { items ->
                CalendarUiState(
                    view = p.view, anchor = p.anchor, today = today, days = days(from, to), items = items,
                    byDay = byDay(items, from, to), canReadDevice = canRead, loaded = true,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial())

    private fun initial(): CalendarUiState {
        val p = position.value
        val (from, to) = CalendarRange.of(p.view, p.anchor)
        return CalendarUiState(view = p.view, anchor = p.anchor, today = today, days = days(from, to), canReadDevice = access.value)
    }

    private fun days(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()

    fun setView(view: CalendarView) = position.update { it.copy(view = view) }
    fun next() = position.update { it.copy(anchor = CalendarRange.step(it.view, it.anchor, forward = true)) }
    fun previous() = position.update { it.copy(anchor = CalendarRange.step(it.view, it.anchor, forward = false)) }
    fun goToday() = position.update { it.copy(anchor = today) }
    fun openDay(date: LocalDate) = position.update { Position(CalendarView.DAY, date) }

    /** Called when the screen comes back, and after the calendar permission was answered. */
    fun onAccessResult() {
        access.value = device.canRead()
        viewModelScope.launch { device.onAccessChanged() }
    }

    fun recheckAccess() {
        if (access.value != device.canRead()) onAccessResult()
    }
}
