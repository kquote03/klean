package app.klean.feature.tasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.core.capture.voice.VoiceSession
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.feature.tasks.data.Completion
import app.klean.feature.tasks.data.TaskSource
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** The local date, now and whenever it changes (checked once a minute). Faked in tests. */
open class DayTicker @Inject constructor(private val clock: Clock, private val zone: ZoneProvider) {
    open fun days(): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds().toLocal(zone.current()).date)
            delay(60_000)
        }
    }.distinctUntilChanged()
}

enum class TasksTab { TODAY, UPCOMING, LISTS, REMINDERS, DONE }

data class TasksUiState(
    val views: TaskViewsModel = TaskViewsModel(),
    val today: LocalDate? = null,
    /** The Reminders tab shows only while the Reminders module is on. */
    val remindersOn: Boolean = true,
    val loaded: Boolean = false,
)

/** Something to say once in a snackbar. */
sealed interface TasksEvent {
    data class Completed(val completion: Completion, val title: String) : TasksEvent
    data class Added(val id: String, val title: String) : TasksEvent
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repo: TasksRepository,
    days: DayTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
    gate: ModuleGate,
    recognizer: VoiceRecognizer,
) : ViewModel() {
    private val selectedList = MutableStateFlow<String?>(null)
    val tab = MutableStateFlow(TasksTab.TODAY)

    /** The add field's text, kept here so voice Undo can put the words back. */
    val draft = MutableStateFlow("")

    private val _events = MutableSharedFlow<TasksEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TasksEvent> = _events

    val voice = VoiceSession(
        scope = viewModelScope,
        recognizer = recognizer,
        save = { text -> addNow(text) },
        undo = { u ->
            repo.delete(u.recordId)
            draft.value = u.text
        },
    )

    init {
        viewModelScope.launch { repo.ensureDefaultLists(); repo.refreshRemote() }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        repo.observeAll(), repo.observeLists(), days.days(), selectedList,
        gate.disabledIds.map { ModuleId.REMINDERS !in it }.distinctUntilChanged(),
    ) { all, lists, today, sel, remindersOn ->
        TasksUiState(TaskViews.build(all, lists, today, sel), today, remindersOn, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun selectTab(t: TasksTab) { tab.value = t }
    fun selectList(id: String) { selectedList.value = id }

    /** Adds the typed text as a task (date words become the due date), into the list on screen when on Lists. */
    fun submit() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        draft.value = ""
        viewModelScope.launch {
            val id = addNow(text)
            _events.tryEmit(TasksEvent.Added(id, text))
        }
    }

    private suspend fun addNow(text: String): String {
        val now = clock.now().toEpochMilliseconds().toLocal(zone.current())
        val list = if (tab.value == TasksTab.LISTS) uiState.value.views.selectedListId else null
        var d = TaskText.parse(text, now, list, TaskSource.MANUAL)
        // Added from the Today tab with no date of its own: it's for today.
        if (tab.value == TasksTab.TODAY && d.dueDate == null) d = d.copy(dueDate = now.date)
        return repo.add(d)
    }

    fun setDone(id: String, done: Boolean, title: String = "") = viewModelScope.launch {
        if (done) _events.tryEmit(TasksEvent.Completed(repo.complete(id), title)) else repo.reopen(id)
    }

    fun undoComplete(c: Completion) = viewModelScope.launch { repo.undoComplete(c) }
    fun undoAdd(id: String) = viewModelScope.launch { repo.delete(id) }

    fun keepForToday(id: String) = viewModelScope.launch { repo.keepForToday(id) }
    fun moveTo(id: String, date: LocalDate) = viewModelScope.launch { repo.moveTo(id, date) }
    fun someday(id: String) = viewModelScope.launch { repo.someday(id) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun addList(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) selectedList.value = repo.addList(name)
    }
    fun renameList(id: String, name: String) = viewModelScope.launch { if (name.isNotBlank()) repo.renameList(id, name) }
    fun deleteList(id: String) = viewModelScope.launch {
        repo.deleteList(id)
        selectedList.value = null
    }

    override fun onCleared() = voice.close()
}
