package app.klean.feature.tasks

import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.feature.tasks.data.Completion
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TaskStatus
import app.klean.feature.tasks.data.TasksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** In-memory [TasksRepository] for view-model tests. Recurrence isn't modelled. */
class FakeTasksRepository(var todayDate: LocalDate = LocalDate(2026, 11, 9)) : TasksRepository {
    private var seq = 0L
    private fun sync(deleted: Boolean = false) = SyncColumns(++seq, seq, "test", deleted, seq)
    val tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val lists = MutableStateFlow(
        listOf(TaskListEntity("p", "Personal", "PERSONAL", 0, sync = sync()), TaskListEntity("w", "Work", "WORK", 1, sync = sync())),
    )
    val drafts = mutableListOf<TaskDraft>()

    override fun observeLists(): Flow<List<TaskListEntity>> = lists.map { l -> l.filter { !it.sync.deleted } }
    override fun observeAll(): Flow<List<TaskEntity>> = tasks.map { l -> l.filter { !it.sync.deleted && it.archivedAt == null } }
    override suspend fun get(id: String) = tasks.value.firstOrNull { it.id == id }
    override suspend fun subtasks(parentId: String) = tasks.value.filter { it.parentId == parentId && !it.sync.deleted }
    override suspend fun ensureDefaultLists() = Unit
    override suspend fun addList(name: String): String {
        val id = "list${++seq}"
        lists.value += TaskListEntity(id, name.trim(), "CUSTOM", lists.value.size, sync = sync())
        return id
    }
    override suspend fun renameList(id: String, name: String) {
        lists.value = lists.value.map { if (it.id == id) it.copy(name = name) else it }
    }
    override suspend fun deleteList(id: String) {
        lists.value = lists.value.map { if (it.id == id) it.copy(sync = sync(true)) else it }
    }
    override suspend fun add(draft: TaskDraft): String {
        drafts += draft
        val id = "t${++seq}"
        tasks.value += TaskEntity(
            id = id, listId = draft.listId ?: "p", title = draft.title.trim(), notes = draft.notes,
            dueDate = draft.dueDate?.toString(), dueMinutes = draft.dueMinutes, priority = draft.priority,
            recurrence = draft.recurrence, parentId = draft.parentId, source = draft.source, sync = sync(),
        )
        return id
    }
    private fun edit(id: String, f: (TaskEntity) -> TaskEntity) {
        tasks.value = tasks.value.map { if (it.id == id) f(it) else it }
    }
    override suspend fun update(task: TaskEntity) = edit(task.id) { task }
    override suspend fun complete(id: String): Completion {
        edit(id) { it.copy(status = TaskStatus.DONE, completedAt = 1L) }
        return Completion(id, null)
    }
    override suspend fun undoComplete(c: Completion) = reopen(c.id)
    override suspend fun reopen(id: String) = edit(id) { it.copy(status = TaskStatus.OPEN, completedAt = null) }
    override suspend fun delete(id: String) = edit(id) { it.copy(sync = sync(true)) }
    override suspend fun pastDue() = tasks.value.filter {
        !it.sync.deleted && it.completedAt == null && it.dueDate != null && it.dueDate!! < todayDate.toString()
    }
    override suspend fun keepForToday(id: String) = moveTo(id, todayDate)
    override suspend fun moveTo(id: String, date: LocalDate) = edit(id) { it.copy(dueDate = date.toString()) }
    override suspend fun someday(id: String) = edit(id) { it.copy(status = TaskStatus.SOMEDAY, dueDate = null) }
    override suspend fun archiveOldCompleted() = 0
    override fun today() = todayDate
}
