package app.klean.core.google

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.nameUuid
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first

/** Where a task or list lives (`task.origin`, `task_list.source`). */
object TaskOrigin {
    const val KLEAN = "KLEAN"
    const val GOOGLE = "GOOGLE"
}

sealed interface GoogleSyncResult {
    data object NotConnected : GoogleSyncResult
    data class Done(val lists: Int, val tasks: Int, val pushed: Int) : GoogleSyncResult
    /** Network or server trouble; the outbox keeps every unsent edit. */
    data class Failed(val retry: Boolean) : GoogleSyncResult
}

/**
 * Keeps the Google Tasks overlay in step: first replays queued edits (PATCH, last write wins), then reads every
 * list and the tasks of the selected ones into `task_list`/`task` rows with origin GOOGLE. Rows are written
 * straight to the DAOs, so pulling never queues an edit back to Google. Klean never creates or deletes a
 * Google task.
 */
class GoogleTasksSync(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val auth: GoogleAuth,
    private val api: GoogleTasksApi,
    private val state: GoogleLocalState,
    private val clock: Clock,
) {
    private val tasks get() = db.taskDao()
    private val lists get() = db.taskListDao()

    suspend fun run(): GoogleSyncResult {
        if (!auth.connected.first()) return GoogleSyncResult.NotConnected
        return try {
            val pushed = replay()
            val (l, t) = pull()
            state.setLastSyncAt(clock.now().toEpochMilliseconds())
            GoogleSyncResult.Done(l, t, pushed)
        } catch (e: GoogleNotConnectedException) {
            GoogleSyncResult.NotConnected
        } catch (e: GoogleHttpException) {
            GoogleSyncResult.Failed(retry = e.status >= 500 || e.status == 429)
        } catch (e: IOException) {
            GoogleSyncResult.Failed(retry = true)
        }
    }

    /** Sends queued edits in order. A task Google no longer has (404) or refuses (other 4xx) is dropped. */
    private suspend fun replay(): Int {
        var sent = 0
        for (op in state.outbox()) {
            try {
                val g = api.patch(op.remoteListId, op.remoteTaskId, op.toPatch())
                db.transact {
                    val row = tasks.get(op.taskId) ?: return@transact
                    if (row.remoteEtag != g.etag) tasks.upsert(row.copy(remoteEtag = g.etag, sync = stamper.stamp(row.sync)))
                }
                sent++
            } catch (e: GoogleHttpException) {
                if (e.status >= 500 || e.status == 429) throw e
            }
            state.remove(op)
        }
        return sent
    }

    private fun OutboxOp.toPatch() = GTaskPatch(
        title = title, notes = notes, due = due, done = done,
        completedAt = if (done) Instant.fromEpochMilliseconds(queuedAt).toString() else null,
    )

    /** Returns (lists seen, task rows written). */
    private suspend fun pull(): Pair<Int, Int> {
        val remote = lists()
        val pending = state.outbox().mapTo(HashSet()) { it.remoteTaskId }
        var written = 0
        val known = lists.observeAll().first().filter { it.source == TaskOrigin.GOOGLE }
        db.transact {
            remote.forEachIndexed { i, rl ->
                val id = listId(rl.id)
                val prev = lists.get(id)
                val wanted = TaskListEntity(
                    id = id, name = rl.title.ifBlank { "Google" }, kind = "CUSTOM", position = GOOGLE_POSITION + i,
                    source = TaskOrigin.GOOGLE, remoteId = rl.id,
                    selected = prev?.takeUnless { it.sync.deleted }?.selected ?: true,
                    sync = prev?.sync ?: PLACEHOLDER,
                )
                if (prev == null || prev.sync.deleted || prev != wanted) lists.upsert(wanted.copy(sync = stamper.stamp(prev?.sync, deleted = false)))
            }
            val remoteIds = remote.mapTo(HashSet()) { it.id }
            known.filter { it.remoteId !in remoteIds }.forEach { gone ->
                lists.upsert(gone.copy(sync = stamper.stamp(gone.sync, deleted = true)))
                tasks.inList(gone.id).forEach { tombstone(it) }
            }
        }
        for (rl in remote) {
            val list = lists.get(listId(rl.id)) ?: continue
            if (!list.selected) {
                db.transact { tasks.inList(list.id).forEach { tombstone(it) } }
                continue
            }
            val items = api.tasks(rl.id).filter { !it.deleted && !it.hidden }
            db.transact {
                val seen = HashSet<String>()
                items.forEachIndexed { i, g ->
                    val id = taskId(g.id)
                    seen += id
                    if (g.id in pending) return@forEachIndexed
                    val prev = tasks.get(id)
                    val merged = merge(prev, g, list, i)
                    if (prev == null || prev.sync.deleted || merged != prev) {
                        tasks.upsert(merged.copy(sync = stamper.stamp(prev?.sync, deleted = false)))
                        written++
                    }
                }
                tasks.inList(list.id)
                    .filter { it.origin == TaskOrigin.GOOGLE && it.id !in seen && it.remoteId !in pending }
                    .forEach { tombstone(it) }
            }
        }
        return remote.size to written
    }

    private suspend fun lists(): List<GTaskList> = api.lists()

    /** The Google fields win; Klean-only fields (priority, reminders' links, archive) are kept. */
    private fun merge(prev: TaskEntity?, g: GTask, list: TaskListEntity, index: Int): TaskEntity {
        val due = g.due?.take(10)
        val base = prev ?: TaskEntity(id = taskId(g.id), listId = list.id, title = "", sync = PLACEHOLDER)
        return base.copy(
            listId = list.id,
            title = g.title.orEmpty(),
            notes = g.notes?.takeIf { it.isNotBlank() },
            dueDate = due,
            // Google keeps no time: a time set in Klean stays while the date is unchanged.
            dueMinutes = if (prev != null && prev.dueDate == due) prev.dueMinutes else null,
            recurrence = null,
            completedAt = if (g.isDone) prev?.completedAt ?: g.completed?.let(::parseMs) ?: clock.now().toEpochMilliseconds() else null,
            status = when {
                g.isDone -> "DONE"
                prev?.status == "SOMEDAY" && due == null -> "SOMEDAY"
                else -> "OPEN"
            },
            parentId = g.parent?.let(::taskId),
            position = index,
            origin = TaskOrigin.GOOGLE,
            remoteId = g.id,
            remoteListId = list.remoteId,
            remoteEtag = g.etag,
            archivedAt = if (g.isDone) prev?.archivedAt else null,
            sync = prev?.sync ?: PLACEHOLDER,
        )
    }

    private fun parseMs(s: String): Long? = runCatching { Instant.parse(s).toEpochMilliseconds() }.getOrNull()

    private suspend fun tombstone(t: TaskEntity) {
        if (!t.sync.deleted) tasks.upsert(t.copy(sync = stamper.stamp(t.sync, deleted = true)))
    }

    /** Disconnect: Google lists and tasks leave Klean on this account. Nothing changes in Google. */
    suspend fun forget() {
        state.clearOutbox()
        val google = lists.observeAll().first().filter { it.source == TaskOrigin.GOOGLE }
        db.transact {
            google.forEach { l ->
                tasks.inList(l.id).forEach { tombstone(it) }
                lists.upsert(l.copy(sync = stamper.stamp(l.sync, deleted = true)))
            }
        }
    }

    /** Shows or hides one Google list in Klean (Settings › Tasks). */
    suspend fun setSelected(listId: String, selected: Boolean) {
        db.transact {
            val l = lists.get(listId)?.takeIf { it.source == TaskOrigin.GOOGLE } ?: return@transact
            lists.upsert(l.copy(selected = selected, sync = stamper.stamp(l.sync)))
            if (!selected) tasks.inList(l.id).forEach { tombstone(it) }
        }
    }

    companion object {
        /** Google lists sort after Klean's own lists. */
        const val GOOGLE_POSITION = 1_000
        fun listId(remoteId: String): String = nameUuid("google.list.$remoteId")
        fun taskId(remoteId: String): String = nameUuid("google.task.$remoteId")
        private val PLACEHOLDER = SyncColumns(createdAt = 0, updatedAt = 0, deviceId = "", deleted = false, changeSeq = 0)
    }
}

/** Turns a local edit of a Google task into the outbox entry that replays it. */
fun TaskEntity.toOutboxOp(now: Long): OutboxOp? {
    if (origin != TaskOrigin.GOOGLE) return null
    val remote = remoteId ?: return null
    val remoteList = remoteListId ?: return null
    return OutboxOp(
        taskId = id, remoteListId = remoteList, remoteTaskId = remote,
        title = title, notes = notes, due = dueDate, done = completedAt != null, queuedAt = now,
    )
}
