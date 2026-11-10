package app.klean.core.data.sleep

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SleepScheduleEntity
import app.klean.core.data.db.transact
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.nameUuid
import app.klean.shared.time.QuietHours
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** Where the sleep window (quiet hours) comes from. `QuietHoursService` and the quiet-hours editor read it. */
interface QuietHoursSource {
    val quietHours: Flow<QuietHours>
    suspend fun current(): QuietHours = quietHours.first()
}

/** The pre-M20 source: the synced `core.quietHours` key alone (tests and callers that only have settings). */
class SettingsQuietHoursSource(private val settings: SettingsRepository) : QuietHoursSource {
    override val quietHours: Flow<QuietHours> = settings.observe(SyncedKeys.QuietHoursKey)
}

/**
 * M20: the sleep schedule (`sleep_schedule`) is the source of quiet hours, with or without the Sleep module.
 *
 * - One every-day row with a name-based id ([PRIMARY_ID]), so two devices that migrate agree on it.
 * - [ensureMigrated] copies the old synced key into that row once: a row that exists (even tombstoned)
 *   is never overwritten by the key again.
 * - Until the row exists the old key is the window, so readers see the same value before and after.
 * - Every write also mirrors the window into `core.quietHours`, so readers that still use the key (diet,
 *   which is frozen, and older app versions) stay in step.
 */
@Singleton
class SleepScheduleRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val settings: SettingsRepository,
) : QuietHoursSource {
    private val dao get() = db.sleepScheduleDao()

    override val quietHours: Flow<QuietHours> =
        combine(dao.observe(PRIMARY_ID), settings.observe(SyncedKeys.QuietHoursKey)) { row, key -> row?.live()?.toQuietHours() ?: key }
            .distinctUntilChanged()

    override suspend fun current(): QuietHours = dao.get(PRIMARY_ID)?.live()?.toQuietHours() ?: settings.get(SyncedKeys.QuietHoursKey)

    /** The schedule (migrated first), with the bedtime-reminder lead time. */
    suspend fun primary(): SleepScheduleEntity {
        ensureMigrated()
        return dao.get(PRIMARY_ID)?.live() ?: default(settings.get(SyncedKeys.QuietHoursKey))
    }

    /** Like [primary], live. Before migration it shows the old key's window with the default lead time. */
    fun observePrimary(): Flow<SleepScheduleEntity> =
        combine(dao.observe(PRIMARY_ID), settings.observe(SyncedKeys.QuietHoursKey)) { row, key -> row?.live() ?: default(key) }
            .distinctUntilChanged()

    /** Copies `core.quietHours` into the first schedule row, once ever. Safe to call often. */
    suspend fun ensureMigrated() {
        if (dao.get(PRIMARY_ID) != null) return
        val key = settings.get(SyncedKeys.QuietHoursKey)
        db.transact {
            if (dao.get(PRIMARY_ID) == null) dao.upsert(default(key).copy(sync = stamper.stamp()))
        }
    }

    /** The quiet-hours editor and the Sleep settings write here. Callers replan alarms afterwards. */
    suspend fun setWindow(q: QuietHours) {
        ensureMigrated()
        db.transact {
            val prev = dao.get(PRIMARY_ID)!!
            dao.upsert(prev.copy(bedMinutes = q.startMinute, wakeMinutes = q.endMinute, sync = stamper.stamp(prev.sync, deleted = false)))
        }
        settings.set(SyncedKeys.QuietHoursKey, q)
    }

    suspend fun setReminderMinutesBefore(minutes: Int) {
        ensureMigrated()
        db.transact {
            val prev = dao.get(PRIMARY_ID)!!
            dao.upsert(prev.copy(reminderMinutesBefore = minutes.coerceIn(0, 180), sync = stamper.stamp(prev.sync, deleted = false)))
        }
    }

    private fun SleepScheduleEntity.live() = takeUnless { it.sync.deleted }

    private fun default(q: QuietHours) = SleepScheduleEntity(
        id = PRIMARY_ID, bedMinutes = q.startMinute, wakeMinutes = q.endMinute, days = EVERY_DAY,
        reminderMinutesBefore = DEFAULT_REMINDER_MINUTES, sync = PLACEHOLDER_SYNC,
    )

    companion object {
        val PRIMARY_ID: String = nameUuid("sleep.schedule.primary")
        const val EVERY_DAY = 127
        const val DEFAULT_REMINDER_MINUTES = 30
        private val PLACEHOLDER_SYNC = app.klean.core.data.db.entity.SyncColumns(0, 0, "", false, 0)
    }
}

fun SleepScheduleEntity.toQuietHours(): QuietHours = QuietHours(bedMinutes, wakeMinutes)

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepScheduleModule {
    @Binds abstract fun quietHoursSource(impl: SleepScheduleRepository): QuietHoursSource
}
