package app.klean.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.reminder.ReminderRepository
import app.klean.feature.reminders.ui.RepeatChoice
import app.klean.feature.reminders.ui.RepeatKind
import app.klean.feature.reminders.ui.Repeats
import app.klean.feature.tasks.TaskEditRoute
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class TaskEditState(
    val loaded: Boolean = false,
    val id: String? = null,
    val parentId: String? = null,
    val title: String = "",
    val notes: String = "",
    val listId: String? = null,
    val dueDate: LocalDate? = null,
    val dueMinutes: Int? = null,
    val priority: Int = 0,
    val repeat: RepeatChoice = RepeatChoice(RepeatKind.NONE),
    val someday: Boolean = false,
    val done: Boolean = false,
    /** Lives in Google Tasks: no Delete, the list is fixed (M21). */
    val google: Boolean = false,
    /** Set once saved or deleted: the screen closes. */
    val closed: Boolean = false,
) {
    val isNew get() = id == null
    val canSave get() = title.isNotBlank() && (repeat.kind != RepeatKind.CUSTOM || Repeats.isValidCustom(repeat.custom))
}

enum class ReminderPreset { AT_DUE, HOUR_BEFORE }

/**
 * A task's page: title, notes, list, due date and time, priority, repeat (presets, every N days/weeks, a
 * custom RRULE), steps (subtasks) and reminders. Steps and reminders need the task saved first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: TasksRepository,
    private val reminders: ReminderRepository,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val route = handle.toRoute<TaskEditRoute>()
    private val _state = MutableStateFlow(TaskEditState())
    val state: StateFlow<TaskEditState> = _state.asStateFlow()

    /** Klean's own lists: tasks are never created in, or moved into, a Google list. */
    val lists: StateFlow<List<TaskListEntity>> =
        repo.observeLists().map { l -> l.filter { it.source != "GOOGLE" } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val idFlow = MutableStateFlow(route.taskId)
    val subtasks: StateFlow<List<TaskEntity>> = idFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeAll().map { l -> l.filter { it.parentId == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val taskReminders: StateFlow<List<ReminderEntity>> = idFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else reminders.observeForTask(id).map { l -> l.filter { !it.sync.deleted } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val t = route.taskId?.let { repo.get(it) }
            _state.value = if (t != null) {
                TaskEditState(
                    loaded = true, id = t.id, parentId = t.parentId, title = t.title, notes = t.notes.orEmpty(),
                    listId = t.listId, dueDate = t.dueDate?.let(LocalDate::parse), dueMinutes = t.dueMinutes,
                    priority = t.priority, repeat = Repeats.choiceOf(t.recurrence), someday = t.status == "SOMEDAY",
                    done = t.completedAt != null, google = t.origin == "GOOGLE",
                )
            } else {
                val parentList = route.parentId?.let { repo.get(it)?.listId }
                TaskEditState(loaded = true, parentId = route.parentId, listId = parentList)
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setList(v: String) = _state.update { it.copy(listId = v) }
    fun setDue(v: LocalDate?) = _state.update { it.copy(dueDate = v, dueMinutes = if (v == null) null else it.dueMinutes, someday = it.someday && v == null) }
    fun setTime(v: Int?) = _state.update { it.copy(dueMinutes = v, dueDate = it.dueDate ?: if (v != null) today() else null) }
    fun setPriority(v: Int) = _state.update { it.copy(priority = v) }
    fun setRepeat(v: RepeatChoice) = _state.update { it.copy(repeat = v) }

    private fun today() = clock.now().toEpochMilliseconds().toLocal(zone.current()).date

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val rule = if (s.repeat.kind == RepeatKind.NONE) null else Repeats.ruleFor(s.repeat, s.dueDate ?: today())
            val existing = s.id?.let { repo.get(it) }
            if (existing == null) {
                repo.add(
                    TaskDraft(
                        title = s.title, listId = s.listId, notes = s.notes, dueDate = s.dueDate, dueMinutes = s.dueMinutes,
                        priority = s.priority, recurrence = rule, parentId = s.parentId,
                    ),
                )
            } else {
                repo.update(
                    existing.copy(
                        title = s.title, notes = s.notes.trim().ifEmpty { null }, listId = s.listId ?: existing.listId,
                        dueDate = s.dueDate?.toString(), dueMinutes = s.dueMinutes.takeIf { s.dueDate != null },
                        priority = s.priority, recurrence = rule,
                        // Giving a someday task a date brings it back.
                        status = if (existing.status == "SOMEDAY" && s.dueDate != null) "OPEN" else existing.status,
                    ),
                )
            }
            _state.update { it.copy(closed = true) }
        }
    }

    fun delete() {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            taskReminders.value.forEach { reminders.delete(it.id) }
            repo.delete(id)
            _state.update { it.copy(closed = true) }
        }
    }

    fun setDone(done: Boolean) {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            if (done) repo.complete(id) else repo.reopen(id)
            _state.update { it.copy(done = done) }
        }
    }

    // Steps.
    fun addSubtask(title: String) {
        val id = _state.value.id ?: return
        if (title.isBlank()) return
        viewModelScope.launch { repo.add(TaskDraft(title = title, parentId = id)) }
    }

    fun setSubtaskDone(subId: String, done: Boolean) = viewModelScope.launch {
        if (done) repo.complete(subId) else repo.reopen(subId)
    }

    fun deleteSubtask(subId: String) = viewModelScope.launch { repo.delete(subId) }

    // Reminders.
    /** When a preset reminder would fire: at the due time (09:00 for an all-day task), or an hour before. */
    fun presetTime(p: ReminderPreset): Long? {
        val s = _state.value
        val date = s.dueDate ?: return null
        val at = atMinute(date, s.dueMinutes ?: DEFAULT_MINUTE, zone.current())
        return if (p == ReminderPreset.AT_DUE) at else at - HOUR_MS
    }

    fun addReminder(p: ReminderPreset) {
        val s = _state.value
        val id = s.id ?: return
        val at = presetTime(p) ?: return
        viewModelScope.launch { reminders.add(s.title.trim(), at, zone.current().id, isAlarm = false, taskId = id) }
    }

    fun setReminderAlarm(r: ReminderEntity, alarm: Boolean) = viewModelScope.launch { reminders.update(r.copy(isAlarm = alarm)) }
    fun deleteReminder(id: String) = viewModelScope.launch { reminders.delete(id) }

    companion object {
        const val DEFAULT_MINUTE = 9 * 60
    }
}
