package app.klean.feature.reminders.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.device.DeviceRepository
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SCHEDULED
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.notifications.AlarmScheduler
import app.klean.feature.reminders.ReminderActions
import app.klean.feature.reminders.ReminderEditRoute
import app.klean.feature.reminders.Snooze
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** The Reminders tab. Its quick field understands "at 5 call Ahmed", "alarm 6:30". */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    db: KleanDatabase,
    private val repo: ReminderRepository,
    private val actions: ReminderActions,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val minutes = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(60_000)
        }
    }

    val groups: StateFlow<ReminderGroups> = combine(db.reminderDao().observeAll(), minutes) { all, now ->
        ReminderGroups.build(all, now, zone.current())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderGroups())

    val draft = MutableStateFlow("")

    fun submit() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.value = ""
        viewModelScope.launch {
            val d = ReminderText.parse(text, clock.now().toEpochMilliseconds(), zone.current())
            repo.add(d.title, d.fireAt, zone.current().id, d.isAlarm)
        }
    }

    fun done(id: String) = viewModelScope.launch { actions.done(id) }
    fun dismiss(id: String) = viewModelScope.launch { actions.dismiss(id) }
    fun snooze(id: String, s: Snooze) = viewModelScope.launch { actions.snooze(id, s) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}

data class ReminderEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val title: String = "",
    val date: LocalDate? = null,
    val minute: Int = 9 * 60,
    val isAlarm: Boolean = false,
    val repeat: RepeatChoice = RepeatChoice(RepeatKind.NONE),
    val taskId: String? = null,
    val saved: Boolean = false,
) {
    val canSave get() = title.isNotBlank() && date != null && (repeat.kind != RepeatKind.CUSTOM || Repeats.isValidCustom(repeat.custom))
}

/** Adds or edits one reminder: title, day and time, repeat, alarm. */
@HiltViewModel
class ReminderEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val db: KleanDatabase,
    private val repo: ReminderRepository,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val route = handle.toRoute<ReminderEditRoute>()
    private val _state = MutableStateFlow(ReminderEditState())
    val state: StateFlow<ReminderEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = route.reminderId?.let { db.reminderDao().get(it) }
            _state.value = if (existing != null) {
                val local = existing.fireAt.toLocal(zone.current())
                ReminderEditState(
                    loaded = true, isNew = false, title = existing.title, date = local.date,
                    minute = local.hour * 60 + local.minute, isAlarm = existing.isAlarm,
                    repeat = Repeats.choiceOf(existing.recurrence), taskId = existing.taskId,
                )
            } else {
                val now = clock.now().toEpochMilliseconds().toLocal(zone.current())
                // Next whole hour today, so a new reminder starts somewhere sensible.
                val nextHour = (now.hour + 1).coerceAtMost(23) * 60
                ReminderEditState(loaded = true, title = route.title.orEmpty(), date = now.date, minute = nextHour, taskId = route.taskId)
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setDate(v: LocalDate) = _state.update { it.copy(date = v) }
    fun setMinute(v: Int) = _state.update { it.copy(minute = v) }
    fun setAlarm(v: Boolean) = _state.update { it.copy(isAlarm = v) }
    fun setRepeat(v: RepeatChoice) = _state.update { it.copy(repeat = v) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val tz = zone.current()
            val at = LocalDateTime(s.date!!, LocalTime(s.minute / 60, s.minute % 60)).toInstant(tz).toEpochMilliseconds()
            val rule = Repeats.ruleFor(s.repeat, s.date)
            val id = route.reminderId
            val existing = id?.let { db.reminderDao().get(it) }
            if (existing == null) {
                repo.add(s.title, at, tz.id, s.isAlarm, taskId = s.taskId, recurrence = rule)
            } else {
                repo.update(
                    existing.copy(
                        title = s.title.trim(), fireAt = at, zone = tz.id, isAlarm = s.isAlarm, recurrence = rule,
                        state = SCHEDULED, snoozedUntil = null,
                    ),
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = route.reminderId ?: return
        viewModelScope.launch {
            repo.delete(id)
            _state.update { it.copy(saved = true) }
        }
    }
}

data class RemindersSettingsState(
    val mainPhone: Boolean = true,
    val exactAlarms: Boolean = true,
    val dndAccess: Boolean = false,
    val fullScreen: Boolean = true,
)

@HiltViewModel
class RemindersSettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    prefs: DevicePrefs,
    private val devices: DeviceRepository,
    private val alarms: AlarmScheduler,
) : ViewModel() {
    private val system = MutableStateFlow(readSystem())

    val state: StateFlow<RemindersSettingsState> = combine(prefs.isMainPhone, system) { main, sys -> sys.copy(mainPhone = main) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersSettingsState())

    /** Re-read what the user may have changed in system settings (call on resume). */
    fun refresh() { system.value = readSystem() }

    private fun readSystem(): RemindersSettingsState {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return RemindersSettingsState(
            exactAlarms = alarms.canExact(),
            dndAccess = nm?.isNotificationPolicyAccessGranted == true,
            fullScreen = Build.VERSION.SDK_INT < 34 || nm?.canUseFullScreenIntent() == true,
        )
    }

    fun setMainPhone(v: Boolean) = viewModelScope.launch { devices.setMainPhone(v) }
}
