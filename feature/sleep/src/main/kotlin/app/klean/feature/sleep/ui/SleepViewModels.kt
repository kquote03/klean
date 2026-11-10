package app.klean.feature.sleep.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.data.sleep.toQuietHours
import app.klean.core.notifications.Rescheduler
import app.klean.feature.sleep.R
import app.klean.feature.sleep.data.Night
import app.klean.feature.sleep.data.SleepMath
import app.klean.feature.sleep.data.SleepRepository
import app.klean.feature.sleep.notify.SleepKeys
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import app.klean.shared.time.minuteOfDay
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.daysUntil

/** Display text for sleep, in the device zone: "23:40", "7 h 20 m", "Today", "Mon 9 Nov". */
class SleepFormatter @Inject constructor(@ApplicationContext private val ctx: Context, private val zone: ZoneProvider) {
    fun time(at: Long): String {
        val f = DateFormat.getTimeFormat(ctx)
        f.timeZone = TimeZone.getTimeZone(zone.current().id)
        return f.format(Date(at))
    }

    fun minute(m: Int): String = time(atMinute(LocalDate(2026, 1, 1), m, zone.current()))

    fun duration(ms: Long): String {
        val (h, m) = SleepMath.hoursMinutes(ms)
        return when {
            h == 0 -> ctx.getString(R.string.sleep_duration_m, m)
            m == 0 -> ctx.getString(R.string.sleep_duration_h, h)
            else -> ctx.getString(R.string.sleep_duration_hm, h, m)
        }
    }

    fun range(s: SleepSessionEntity): String = ctx.getString(R.string.sleep_range, time(s.bedAt), s.wakeAt?.let { time(it) } ?: "…")

    fun day(date: LocalDate, today: LocalDate): String = when (date.daysUntil(today)) {
        0 -> ctx.getString(R.string.sleep_day_today)
        1 -> ctx.getString(R.string.sleep_day_yesterday)
        else -> {
            val f = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), Locale.getDefault())
            f.timeZone = TimeZone.getTimeZone(zone.current().id)
            f.format(Date(atMinute(date, 12 * 60, zone.current())))
        }
    }
}

/** The clock once a minute, so "today" and the one-tap state follow time without a write. */
open class SleepTicker @Inject constructor(private val clock: Clock) {
    open fun ticks(): Flow<Long> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(60_000)
        }
    }
}

data class SleepState(
    val loaded: Boolean = false,
    val today: LocalDate? = null,
    val open: SleepSessionEntity? = null,
    val lastNight: SleepSessionEntity? = null,
    val nights: List<Night> = emptyList(),
)

/** The Sleep screen and the Today card: one tap to go to bed or get up, and the last 14 nights. */
@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repo: SleepRepository,
    schedule: SleepScheduleRepository,
    ticker: SleepTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    val state: StateFlow<SleepState> = combine(repo.observeSessions(), schedule.quietHours, ticker.ticks()) { sessions, q, now ->
        val today = app.klean.shared.time.DayWindows.logicalDate(now, q, zone.current())
        SleepState(
            loaded = true,
            today = today,
            open = sessions.firstOrNull { it.wakeAt == null },
            lastNight = SleepMath.lastNight(sessions, today, q, zone.current()),
            nights = SleepMath.nights(sessions, today, NIGHTS, q, zone.current()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepState())

    fun toggle() = viewModelScope.launch { repo.toggle(clock.now().toEpochMilliseconds()) }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun restore(id: String) = viewModelScope.launch { repo.restore(id) }

    companion object {
        const val NIGHTS = 14
    }
}

/** Performs one toggle (the widget's and notification's deep link), then hands over to the Sleep screen. */
@HiltViewModel
class SleepToggleViewModel @Inject constructor(private val repo: SleepRepository, private val clock: Clock) : ViewModel() {
    suspend fun toggle(): SleepRepository.Toggle = repo.toggle(clock.now().toEpochMilliseconds())
}

data class SleepEditState(val loaded: Boolean = false, val isNew: Boolean = true, val bedAt: Long = 0, val wakeAt: Long? = null)

/** Edit (or add) a night: bed and wake times; the bed date follows the wake date ("the night before"). */
@HiltViewModel
class SleepEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: SleepRepository,
    private val schedule: SleepScheduleRepository,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val id: String? = handle.get<String>("sessionId")
    private val _state = MutableStateFlow(SleepEditState())
    val state: StateFlow<SleepEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = id?.let { repo.get(it) }
            _state.value = if (s != null) {
                SleepEditState(true, false, s.bedAt, s.wakeAt)
            } else {
                val q = schedule.primary().toQuietHours()
                val now = clock.now().toEpochMilliseconds()
                val wake = SleepMath.latestAtOrBefore(now, listOf(q.endMinute), zone.current())
                val bed = SleepMath.latestAtOrBefore(wake, listOf(q.startMinute), zone.current(), strict = true)
                SleepEditState(true, true, bed, wake)
            }
        }
    }

    /** New bedtime: the latest [minute] before the wake time (or now while still asleep). */
    fun setBed(minute: Int) {
        val s = _state.value
        val ref = s.wakeAt ?: clock.now().toEpochMilliseconds()
        _state.value = s.copy(bedAt = SleepMath.latestAtOrBefore(ref, listOf(minute), zone.current(), strict = true))
    }

    /** New wake time: the first [minute] after bedtime. */
    fun setWake(minute: Int) {
        val s = _state.value
        val z = zone.current()
        val bedDate = s.bedAt.toLocal(z).date
        val sameDay = atMinute(bedDate, minute, z)
        val wake = if (sameDay > s.bedAt) sameDay else atMinute(bedDate.plus(DatePeriod(days = 1)), minute, z)
        _state.value = s.copy(wakeAt = wake)
    }

    fun bedMinute(): Int = minuteOfDay(_state.value.bedAt, zone.current())
    fun wakeMinute(): Int? = _state.value.wakeAt?.let { minuteOfDay(it, zone.current()) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (id == null) repo.log(s.bedAt, s.wakeAt ?: clock.now().toEpochMilliseconds()) else repo.update(id, s.bedAt, s.wakeAt)
        onDone()
    }

    fun delete(onDone: (String) -> Unit) = viewModelScope.launch {
        id?.let { repo.delete(it) }
        onDone(id.orEmpty())
    }
}

data class SleepSettingsState(
    val window: QuietHours = QuietHours.DEFAULT,
    val bedtime: Boolean = true,
    val lead: Int = SleepScheduleRepository.DEFAULT_REMINDER_MINUTES,
    val sessionNotification: Boolean = true,
)

@HiltViewModel
class SleepSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val schedule: SleepScheduleRepository,
    private val rescheduler: Rescheduler,
) : ViewModel() {
    val state: StateFlow<SleepSettingsState> = combine(
        schedule.observePrimary(), settings.observe(SleepKeys.BedtimeReminder), settings.observe(SleepKeys.SessionNotification),
    ) { s, bed, session -> SleepSettingsState(s.toQuietHours(), bed, s.reminderMinutesBefore, session) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepSettingsState())

    /** The schedule is quiet hours: every time-based thing replans (diet check-ins, reminders, bedtime). */
    fun setWindow(q: QuietHours) = viewModelScope.launch {
        schedule.setWindow(q)
        rescheduler.rescheduleAll()
    }

    fun setBedtime(v: Boolean) = viewModelScope.launch {
        settings.set(SleepKeys.BedtimeReminder, v)
        rescheduler.rescheduleAll()
    }

    fun setLead(minutes: Int) = viewModelScope.launch {
        schedule.setReminderMinutesBefore(minutes)
        rescheduler.rescheduleAll()
    }

    fun setSessionNotification(v: Boolean) = viewModelScope.launch {
        settings.set(SleepKeys.SessionNotification, v)
        rescheduler.rescheduleAll()
    }
}
