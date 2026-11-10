package app.klean.feature.tasks.data

import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.db.transact
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.UuidV7
import app.klean.shared.id.nameUuid
import app.klean.shared.time.RecurrenceRule
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** What a new task starts with. [listId] null = the default list ("Personal"). */
data class TaskDraft(
    val title: String,
    val listId: String? = null,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val dueMinutes: Int? = null,
    val priority: Int = 0,
    val recurrence: String? = null,
    val parentId: String? = null,
    val source: String = TaskSource.MANUAL,
)

object TaskSource {
    const val MANUAL = "MANUAL"
    const val CAPTURE = "CAPTURE"
}

object TaskStatus {
    const val OPEN = "OPEN"
    const val DONE = "DONE"
    const val SOMEDAY = "SOMEDAY"
}

/** A completion, kept so the snackbar's Undo can reverse it, including the next occurrence it created. */
data class Completion(val id: String, val nextId: String?)

/** Told after every task or list write (outside the transaction): status line, widget, Today. */
fun interface TaskChangeListener {
    suspend fun onTasksChanged()
}

interface TasksRepository {
    /** Live lists, in order. */
    fun observeLists(): Flow<List<TaskListEntity>>
    /** Live tasks that haven't been archived (open, someday and recently done), subtasks included. */
    fun observeAll(): Flow<List<TaskEntity>>
    suspend fun get(id: String): TaskEntity?
    suspend fun subtasks(parentId: String): List<TaskEntity>

    /** Creates "Personal" and "Work" the first time Tasks is used (synced flag; ids agree across devices). */
    suspend fun ensureDefaultLists()
    suspend fun addList(name: String): String
    suspend fun renameList(id: String, name: String)
    /** Tombstones the list and every task in it. */
    suspend fun deleteList(id: String)

    suspend fun add(draft: TaskDraft): String
    /** Saves the edited fields of [task] as given, restamping its sync columns. */
    suspend fun update(task: TaskEntity)
    /** Done. A recurring task makes its next occurrence (with fresh copies of its subtasks). */
    suspend fun complete(id: String): Completion
    suspend fun undoComplete(c: Completion)
    suspend fun reopen(id: String)
    /** Tombstones the task and its subtasks. */
    suspend fun delete(id: String)

    /** Open tasks due before today: the "Still want these?" review. */
    suspend fun pastDue(): List<TaskEntity>
    suspend fun keepForToday(id: String)
    suspend fun moveTo(id: String, date: LocalDate)
    /** Status SOMEDAY, no date: out of Today and the review, still in its list. */
    suspend fun someday(id: String)

    /** Marks tasks done more than [ARCHIVE_AFTER_DAYS] days ago as archived. Returns how many. */
    suspend fun archiveOldCompleted(): Int

    fun today(): LocalDate

    companion object {
        const val ARCHIVE_AFTER_DAYS = 30
    }
}

@Singleton
class DefaultTasksRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val settings: SettingsRepository,
    private val listeners: Set<@JvmSuppressWildcards TaskChangeListener>,
) : TasksRepository {
    private val tasks get() = db.taskDao()
    private val lists get() = db.taskListDao()

    private fun now() = clock.now().toEpochMilliseconds()
    override fun today(): LocalDate = now().toLocal(zone.current()).date

    override fun observeLists(): Flow<List<TaskListEntity>> = lists.observeAll()
    override fun observeAll(): Flow<List<TaskEntity>> = tasks.observeAll().map { l -> l.filter { it.archivedAt == null } }
    override suspend fun get(id: String): TaskEntity? = tasks.get(id)
    override suspend fun subtasks(parentId: String): List<TaskEntity> = tasks.children(parentId)

    override suspend fun ensureDefaultLists() {
        if (settings.get(TasksKeys.Seeded)) return
        db.transact {
            listOf(Triple(PERSONAL_ID, "Personal", "PERSONAL"), Triple(WORK_ID, "Work", "WORK")).forEachIndexed { i, (id, name, kind) ->
                if (lists.get(id) == null) lists.upsert(TaskListEntity(id, name, kind, position = i, sync = stamper.stamp()))
            }
        }
        settings.set(TasksKeys.Seeded, true)
        notifyChanged()
    }

    override suspend fun addList(name: String): String {
        val id = db.transact {
            val position = (lists.maxPosition() ?: -1) + 1
            UuidV7.generate(now()).also { lists.upsert(TaskListEntity(it, name.trim(), "CUSTOM", position, sync = stamper.stamp())) }
        }
        notifyChanged()
        return id
    }

    override suspend fun renameList(id: String, name: String) {
        db.transact {
            val prev = lists.get(id) ?: return@transact
            lists.upsert(prev.copy(name = name.trim(), sync = stamper.stamp(prev.sync)))
        }
        notifyChanged()
    }

    override suspend fun deleteList(id: String) {
        db.transact {
            val prev = lists.get(id) ?: return@transact
            lists.upsert(prev.copy(sync = stamper.stamp(prev.sync, deleted = true)))
            tasks.inList(id).forEach { tombstone(it) }
        }
        notifyChanged()
    }

    override suspend fun add(draft: TaskDraft): String {
        val id = db.transact { insert(draft) }
        notifyChanged()
        return id
    }

    private suspend fun insert(d: TaskDraft): String {
        val id = UuidV7.generate(now())
        val listId = d.listId ?: d.parentId?.let { tasks.get(it)?.listId } ?: settings.get(TasksKeys.DefaultListId)
        tasks.upsert(
            TaskEntity(
                id = id,
                listId = listId,
                title = d.title.trim(),
                notes = d.notes?.trim()?.ifEmpty { null },
                dueDate = d.dueDate?.toString(),
                dueMinutes = d.dueMinutes.takeIf { d.dueDate != null },
                priority = d.priority,
                recurrence = d.recurrence,
                parentId = d.parentId,
                source = d.source,
                sync = stamper.stamp(),
            ),
        )
        return id
    }

    override suspend fun update(task: TaskEntity) {
        db.transact {
            val prev = tasks.get(task.id) ?: return@transact
            tasks.upsert(task.copy(title = task.title.trim(), sync = stamper.stamp(prev.sync)))
        }
        notifyChanged()
    }

    override suspend fun complete(id: String): Completion {
        val c = db.transact {
            val t = tasks.get(id)?.takeUnless { it.sync.deleted || it.completedAt != null } ?: return@transact Completion(id, null)
            tasks.upsert(t.copy(status = TaskStatus.DONE, completedAt = now(), sync = stamper.stamp(t.sync)))
            Completion(id, nextOccurrence(t))
        }
        notifyChanged()
        return c
    }

    /** The next instance of a recurring task, or null. Subtasks come along, open again. */
    private suspend fun nextOccurrence(t: TaskEntity): String? {
        val rule = t.recurrence?.let(RecurrenceRule::parse) ?: return null
        val from = t.dueDate?.let(LocalDate::parse) ?: today()
        val next = rule.next(from) ?: return null
        val nextId = insert(
            TaskDraft(
                title = t.title, listId = t.listId, notes = t.notes, dueDate = next, dueMinutes = t.dueMinutes,
                priority = t.priority, recurrence = rule.remainingAfterOne()?.format(), parentId = t.parentId, source = t.source,
            ),
        )
        subtasks(t.id).filter { !it.sync.deleted }.forEach { s ->
            insert(TaskDraft(title = s.title, listId = s.listId, notes = s.notes, priority = s.priority, parentId = nextId))
        }
        return nextId
    }

    override suspend fun undoComplete(c: Completion) {
        db.transact {
            c.nextId?.let { n -> tasks.get(n)?.let { tombstone(it) }; subtasks(n).forEach { tombstone(it) } }
            reopenRow(c.id)
        }
        notifyChanged()
    }

    override suspend fun reopen(id: String) {
        db.transact { reopenRow(id) }
        notifyChanged()
    }

    private suspend fun reopenRow(id: String) {
        val t = tasks.get(id) ?: return
        tasks.upsert(t.copy(status = TaskStatus.OPEN, completedAt = null, archivedAt = null, sync = stamper.stamp(t.sync)))
    }

    override suspend fun delete(id: String) {
        db.transact {
            val t = tasks.get(id) ?: return@transact
            subtasks(id).forEach { tombstone(it) }
            tombstone(t)
        }
        notifyChanged()
    }

    private suspend fun tombstone(t: TaskEntity) {
        if (!t.sync.deleted) tasks.upsert(t.copy(sync = stamper.stamp(t.sync, deleted = true)))
    }

    override suspend fun pastDue(): List<TaskEntity> =
        tasks.pastDue(today().toString()).filter { it.status == TaskStatus.OPEN && it.archivedAt == null }

    override suspend fun keepForToday(id: String) = moveTo(id, today())

    override suspend fun moveTo(id: String, date: LocalDate) = edit(id) { it.copy(dueDate = date.toString(), status = TaskStatus.OPEN) }

    override suspend fun someday(id: String) = edit(id) { it.copy(status = TaskStatus.SOMEDAY, dueDate = null, dueMinutes = null) }

    private suspend fun edit(id: String, change: (TaskEntity) -> TaskEntity) {
        db.transact {
            val t = tasks.get(id)?.takeUnless { it.sync.deleted } ?: return@transact
            tasks.upsert(change(t).copy(sync = stamper.stamp(t.sync)))
        }
        notifyChanged()
    }

    override suspend fun archiveOldCompleted(): Int {
        val cutoff = now() - TasksRepository.ARCHIVE_AFTER_DAYS * DAY_MS
        val n = db.transact {
            val old = tasks.completedBefore(cutoff)
            old.forEach { tasks.upsert(it.copy(archivedAt = now(), sync = stamper.stamp(it.sync))) }
            old.size
        }
        if (n > 0) notifyChanged()
        return n
    }

    private suspend fun notifyChanged() = listeners.forEach { it.onTasksChanged() }

    companion object {
        private const val DAY_MS = 86_400_000L
        /** Name-based, so two devices that both seed agree on the rows (and sync merges them). */
        val PERSONAL_ID: String = nameUuid("tasks.list.personal")
        val WORK_ID: String = nameUuid("tasks.list.work")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TasksDataModule {
    @Binds abstract fun repo(impl: DefaultTasksRepository): TasksRepository
    @Multibinds abstract fun listeners(): Set<TaskChangeListener>
}
