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

    override fun observeForTask(taskId: String): Flow<List<ReminderEntity>> = dao.forTask(taskId)

    override fun observeUpcoming(fromMs: Long): Flow<List<ReminderEntity>> = dao.observeUpcoming(fromMs)

    private suspend fun notify(id: String) = listeners.forEach { it.onChanged(id) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun reminders(impl: DefaultReminderRepository): ReminderRepository

    /** Empty until the Reminders module contributes its scheduler. */
    @Multibinds abstract fun reminderListeners(): Set<ReminderChangeListener>
}
