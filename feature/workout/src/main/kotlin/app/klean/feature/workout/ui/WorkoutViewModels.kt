package app.klean.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.workout.data.ExerciseHistoryRow
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.data.WorkoutKeys
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.feature.workout.model.TemplateText
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.suggest.WorkoutSuggestion
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** The clock, once a [periodMs]: elapsed timers and "today" follow it without a write. */
open class WorkoutTicker @Inject constructor(private val clock: Clock) {
    open fun ticks(periodMs: Long = 30_000): Flow<Long> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(periodMs)
        }
    }
}

data class TemplateRow(val template: WorkoutTemplateEntity, val items: List<WorkoutItem>) {
    val text: String get() = TemplateText.format(items)
}

data class WorkoutHomeState(
    val loaded: Boolean = false,
    val today: LocalDate? = null,
    val open: SessionWithSets? = null,
    val templates: List<TemplateRow> = emptyList(),
    val recent: List<SessionWithSets> = emptyList(),
    /** The last seven days, for the hero's bars. */
    val week: WorkoutWeek? = null,
)

/** The last seven days ending on [today], oldest first. */
fun lastSevenDays(today: LocalDate): List<LocalDate> = (6 downTo 0).map { today.minus(kotlinx.datetime.DatePeriod(days = it)) }

sealed interface WorkoutEvent {
    data class Logged(val sessionId: String) : WorkoutEvent
    data class Removed(val sessionId: String) : WorkoutEvent
}

@HiltViewModel
class WorkoutHomeViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    ticker: WorkoutTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
    health: app.klean.feature.workout.HealthConnectBridge = object : app.klean.feature.workout.HealthConnectBridge {},
) : ViewModel() {
    init {
        // Other apps' workouts from Health Connect (M22): best-effort on every visit.
        viewModelScope.launch { runCatching { health.importRecent(app.klean.feature.workout.health.HealthConnectWorkoutBridge.DAYS) } }
    }

    val state: StateFlow<WorkoutHomeState> = combine(repo.observeSessions(), repo.observeTemplates(), ticker.ticks()) { sessions, templates, now ->
        val today = now.toLocal(zone.current()).date
        WorkoutHomeState(
            loaded = true,
            today = today,
            week = WorkoutWeek.of(sessions, lastSevenDays(today)) { it.toLocal(zone.current()).date },
            open = sessions.firstOrNull { it.isOpen },
            templates = templates.map { TemplateRow(it, WorkoutItem.decode(it.items)) },
            recent = sessions.filter { !it.isOpen }.take(RECENT),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeState())

    private val _events = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = _events.receiveAsFlow()

    fun start(templateId: String?, onStarted: (String) -> Unit) = viewModelScope.launch {
        val id = repo.start(templateId)
        onStarted(id)
    }

    fun log(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val id = repo.logText(text, clock.now().toEpochMilliseconds())
            _events.send(WorkoutEvent.Logged(id))
        }
    }

    fun undo(sessionId: String) = viewModelScope.launch { repo.delete(sessionId) }

    fun restore(sessionId: String) = viewModelScope.launch { repo.restore(sessionId) }

    private companion object {
        const val RECENT = 10
    }
}

data class TemplateEditState(val loaded: Boolean = false, val isNew: Boolean = true, val name: String = "", val text: String = "") {
    val preview: List<WorkoutItem> get() = TemplateText.parse(text)
}

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: WorkoutRepository,
) : ViewModel() {
    private val id: String? = handle.get<String>("templateId")
    private val _state = MutableStateFlow(TemplateEditState())
    val state: StateFlow<TemplateEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val t = id?.let { repo.template(it) }
            _state.value = if (t != null) {
                TemplateEditState(true, false, t.name, TemplateText.format(WorkoutItem.decode(t.items)))
            } else {
                TemplateEditState(loaded = true)
            }
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v) }
    fun setText(v: String) { _state.value = _state.value.copy(text = v) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (s.text.isNotBlank() || s.name.isNotBlank()) repo.saveTemplate(id, s.name, s.text)
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        id?.let { repo.deleteTemplate(it) }
        onDone()
    }
}

data class SessionState(
    val loaded: Boolean = false,
    val session: SessionWithSets? = null,
    val rows: List<SessionRow> = emptyList(),
    val now: Long = 0,
    val today: LocalDate? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: WorkoutRepository,
    ticker: WorkoutTicker,
    private val zone: ZoneProvider,
    private val health: app.klean.feature.workout.HealthConnectBridge = object : app.klean.feature.workout.HealthConnectBridge {},
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("sessionId"))

    val state: StateFlow<SessionState> = combine(repo.observeSession(id), repo.observeTemplates(), ticker.ticks(15_000)) { s, templates, now ->
        val planned = s?.session?.templateId?.let { t -> templates.firstOrNull { it.id == t } }?.let { WorkoutItem.decode(it.items) }.orEmpty()
        SessionState(true, s, if (s == null) emptyList() else SessionRows.build(planned, s.sets), now, now.toLocal(zone.current()).date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState())

    fun addSet(row: SessionRow) = viewModelScope.launch { repo.addSet(id, row.next) }

    fun addText(text: String) {
        val items = TemplateText.parse(text)
        if (items.isEmpty()) return
        viewModelScope.launch { repo.logItems(id, items) }
    }

    fun updateSet(set: WorkoutSetEntity, reps: Int?, weightKg: Double?, durationS: Int?, distanceM: Int?) =
        viewModelScope.launch { repo.updateSet(set.id, reps, weightKg, durationS, distanceM) }

    fun deleteSet(set: WorkoutSetEntity) = viewModelScope.launch { repo.deleteSet(set.id) }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        repo.finish(id)
        onDone()
        runCatching { health.writeSession(id) }
    }

    fun delete(onDone: (String) -> Unit) = viewModelScope.launch {
        repo.delete(id)
        onDone(id)
    }
}

@HiltViewModel
class ExercisesViewModel @Inject constructor(repo: WorkoutRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseDefinitionEntity>?> =
        repo.observeExercises().map<List<ExerciseDefinitionEntity>, List<ExerciseDefinitionEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class HistoryState(val name: String = "", val rows: List<ExerciseHistoryRow> = emptyList(), val today: LocalDate? = null)

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: WorkoutRepository,
    clock: Clock,
    zone: ZoneProvider,
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("exerciseId"))
    private val name = MutableStateFlow("")
    private val today = clock.now().toEpochMilliseconds().toLocal(zone.current()).date

    init {
        viewModelScope.launch { name.value = repo.exercise(id)?.name.orEmpty() }
    }

    val state: StateFlow<HistoryState> = combine(name, repo.observeHistory(id)) { n, rows -> HistoryState(n, rows, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())
}

data class WorkoutSettingsState(val suggest: Boolean = false, val days: Int = 3)

@HiltViewModel
class WorkoutSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val suggestion: WorkoutSuggestion,
) : ViewModel() {
    val state: StateFlow<WorkoutSettingsState> = combine(settings.observe(WorkoutKeys.SuggestEnabled), settings.observe(WorkoutKeys.SuggestDays)) { on, d ->
        WorkoutSettingsState(on, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSettingsState())

    fun setSuggest(v: Boolean) = viewModelScope.launch {
        settings.set(WorkoutKeys.SuggestEnabled, v)
        suggestion.reschedule()
    }

    fun setDays(v: Int) = viewModelScope.launch {
        settings.set(WorkoutKeys.SuggestDays, v)
        suggestion.reschedule()
    }
}
