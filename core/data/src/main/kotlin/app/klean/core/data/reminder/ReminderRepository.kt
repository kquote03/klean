package app.klean.core.data.reminder

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.UuidV7
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * Reminders live in `:core:data` so Tasks (and Calendar) can attach one without depending on the
 * Reminders module's UI. The Reminders module arms the alarms by listening ([ReminderChangeListener]).
 */
interface ReminderRepository {
    /** Adds a SCHEDULED reminder and returns its id. [zone] is the IANA zone the time was set in. */
    suspend fun add(
        title: String,
        fireAt: Long,
        zone: String,
        isAlarm: Boolean,
        taskId: String? = null,
        recurrence: String? = null,
    ): String

    /** Saves [r] as given (state, snooze, times), restamping its sync columns. */
    suspend fun update(r: ReminderEntity)

    /** Tombstones the row; it keeps syncing as deleted. */
    suspend fun delete(id: String)

    /** Rings again at [until] (UTC ms): state SNOOZED. */
    suspend fun snooze(id: String, until: Long)

    /**
     * "Done": the user did the thing. With [nextFireAt] (a recurring reminder; the caller owns the
     * rule maths) it is rescheduled there. Otherwise it becomes DONE and an attached, open,
     * non-recurring task is completed too.
     */
    suspend fun markDone(id: String, nextFireAt: Long? = null)

    /**
     * "Dismiss": stop ringing, nothing else. Never completes a task (dismiss ≠ done). With
     * [nextFireAt] it is rescheduled there; otherwise it becomes DISMISSED.
     */
    suspend fun dismiss(id: String, nextFireAt: Long? = null)

    fun observeForTask(taskId: String): Flow<List<ReminderEntity>>

    /** SCHEDULED or SNOOZED reminders due at or after [fromMs] (snoozed ones by their snooze time). */
    fun observeUpcoming(fromMs: Long): Flow<List<ReminderEntity>>
}

/** Told after every reminder write, outside the transaction. The Reminders scheduler is one of these. */
fun interface ReminderChangeListener {
    suspend fun onChanged(id: String)
}

@Singleton
class DefaultReminderRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards ReminderChangeListener>,
) : ReminderRepository {
    private val dao get() = db.reminderDao()

    override suspend fun add(
        title: String,
        fireAt: Long,
        zone: String,
        isAlarm: Boolean,
        taskId: String?,
        recurrence: String?,
    ): String {
        val id = db.transact {
            val rowId = UuidV7.generate(clock.now().toEpochMilliseconds())
            dao.upsert(
                ReminderEntity(
                    id = rowId,
                    taskId = taskId,
                    title = title.trim(),
                    fireAt = fireAt,
                    zone = zone,
                    isAlarm = isAlarm,
                    recurrence = recurrence,
                    sync = stamper.stamp(),
                ),
            )
            rowId
        }
        notify(id)
        return id
    }

    override suspend fun update(r: ReminderEntity) {
        db.transact {
            val prev = dao.get(r.id)
            dao.upsert(r.copy(sync = stamper.stamp(prev?.sync ?: r.sync, deleted = r.sync.deleted)))
        }
        notify(r.id)
    }

    override suspend fun delete(id: String) {
        val found = db.transact {
            val prev = dao.get(id) ?: return@transact false
            dao.upsert(prev.copy(sync = stamper.stamp(prev.sync, deleted = true)))
            true
        }
        if (found) notify(id)
    }

    override suspend fun snooze(id: String, until: Long) =
        change(id) { it.copy(state = SNOOZED, snoozedUntil = until) }

    override suspend fun markDone(id: String, nextFireAt: Long?) = change(id) { r ->
        if (nextFireAt != null) return@change r.rescheduled(nextFireAt)
        r.taskId?.let { taskId ->
            val task = db.taskDao().get(taskId)
            if (task != null && !task.sync.deleted && task.completedAt == null && task.recurrence == null) {
                db.taskDao().upsert(
                    task.copy(status = "DONE", completedAt = clock.now().toEpochMilliseconds(), sync = stamper.stamp(task.sync)),
                )
            }
        }
        r.copy(state = DONE, snoozedUntil = null)
    }

    override suspend fun dismiss(id: String, nextFireAt: Long?) = change(id) { r ->
        if (nextFireAt != null) r.rescheduled(nextFireAt) else r.copy(state = DISMISSED, snoozedUntil = null)
    }

    private fun ReminderEntity.rescheduled(at: Long) = copy(state = SCHEDULED, fireAt = at, snoozedUntil = null)

    /** Read-modify-write of one live reminder in a transaction; listeners hear about it afterwards. */
    private suspend fun change(id: String, edit: suspend (ReminderEntity) -> ReminderEntity) {
        val found = db.transact {
            val prev = dao.get(id)?.takeUnless { it.sync.deleted } ?: return@transact false
            dao.upsert(edit(prev).copy(sync = stamper.stamp(prev.sync)))
            true
        }
        if (found) notify(id)
    }

    override fun observeForTask(taskId: String): Flow<List<ReminderEntity>> = dao.forTask(taskId)

    override fun observeUpcoming(fromMs: Long): Flow<List<ReminderEntity>> = dao.observeUpcoming(fromMs)

    private suspend fun notify(id: String) = listeners.forEach { it.onChanged(id) }

    companion object {
        const val SCHEDULED = "SCHEDULED"
        const val SNOOZED = "SNOOZED"
        const val DONE = "DONE"
        const val DISMISSED = "DISMISSED"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun reminders(impl: DefaultReminderRepository): ReminderRepository

    /** Empty until the Reminders module contributes its scheduler. */
    @Multibinds abstract fun reminderListeners(): Set<ReminderChangeListener>
}
