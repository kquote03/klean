package app.klean.core.data.db

import androidx.room.AutoMigration
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.klean.core.data.db.dao.ApprovalRequestDao
import app.klean.core.data.db.dao.CalendarSourceDao
import app.klean.core.data.db.dao.ChangeCounterDao
import app.klean.core.data.db.dao.ContactDao
import app.klean.core.data.db.dao.DelegationDao
import app.klean.core.data.db.dao.DelegationMessageDao
import app.klean.core.data.db.dao.DeviceDao
import app.klean.core.data.db.dao.EventDao
import app.klean.core.data.db.dao.ExerciseDefinitionDao
import app.klean.core.data.db.dao.FoodEntryDao
import app.klean.core.data.db.dao.FoodItemDao
import app.klean.core.data.db.dao.GeofenceDao
import app.klean.core.data.db.dao.PromptLogDao
import app.klean.core.data.db.dao.ReminderDao
import app.klean.core.data.db.dao.SettingDao
import app.klean.core.data.db.dao.SleepDao
import app.klean.core.data.db.dao.SleepScheduleDao
import app.klean.core.data.db.dao.SyncCursorDao
import app.klean.core.data.db.dao.TaskDao
import app.klean.core.data.db.dao.TaskListDao
import app.klean.core.data.db.dao.WorkoutSessionDao
import app.klean.core.data.db.dao.WorkoutSetDao
import app.klean.core.data.db.dao.WorkoutTemplateDao
import app.klean.core.data.db.entity.ApprovalRequestEntity
import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.core.data.db.entity.ChangeCounterEntity
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.db.entity.DelegationEntity
import app.klean.core.data.db.entity.DelegationMessageEntity
import app.klean.core.data.db.entity.DeviceEntity
import app.klean.core.data.db.entity.EventEntity
import app.klean.core.data.db.entity.ExerciseDefinitionEntity
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.entity.GeofenceEntity
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SettingEntity
import app.klean.core.data.db.entity.SleepScheduleEntity
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.entity.SyncCursorEntity
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity

/** One database for every module, so one file is one backup. Later modules add tables via migrations. */
@Database(
    entities = [
        FoodEntryEntity::class, FoodItemEntity::class, PromptLogEntity::class,
        SettingEntity::class, GeofenceEntity::class, ChangeCounterEntity::class,
        // v2: life modules (P2). Schema is frozen for the module streams; changes go through a parent-made migration.
        TaskListEntity::class, TaskEntity::class, ReminderEntity::class, EventEntity::class, ContactEntity::class,
        WorkoutTemplateEntity::class, WorkoutSessionEntity::class, WorkoutSetEntity::class, SleepSessionEntity::class,
        // v3 (M17): every remaining entity, so module lanes never change the schema. Only adds tables and columns.
        DelegationEntity::class, DelegationMessageEntity::class, ApprovalRequestEntity::class, CalendarSourceEntity::class,
        ExerciseDefinitionEntity::class, SleepScheduleEntity::class, DeviceEntity::class, SyncCursorEntity::class,
    ],
    version = KleanDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = KleanDatabase.V2ToV3::class),
        // v4: food_entry.kcal / kcal_source / grams (plan 2026-11-11, calories). Nullable columns only.
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class KleanDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun promptLogDao(): PromptLogDao
    abstract fun settingDao(): SettingDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun changeCounterDao(): ChangeCounterDao
    abstract fun taskListDao(): TaskListDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun eventDao(): EventDao
    abstract fun contactDao(): ContactDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun sleepDao(): SleepDao
    abstract fun delegationDao(): DelegationDao
    abstract fun delegationMessageDao(): DelegationMessageDao
    abstract fun approvalRequestDao(): ApprovalRequestDao
    abstract fun calendarSourceDao(): CalendarSourceDao
    abstract fun exerciseDefinitionDao(): ExerciseDefinitionDao
    abstract fun sleepScheduleDao(): SleepScheduleDao
    abstract fun deviceDao(): DeviceDao
    abstract fun syncCursorDao(): SyncCursorDao

    /**
     * 2→3 adds tables and columns only. Afterwards, tasks finished before v3 get `status = DONE`
     * (the new column defaults to OPEN), so `status` and `completed_at` agree from the start.
     */
    class V2ToV3 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("UPDATE task SET status = 'DONE' WHERE completed_at IS NOT NULL")
        }
    }

    companion object {
        const val VERSION = 4
        const val FILE_NAME = "klean.db"
    }
}

/**
 * Runs [block] in an immediate (write-locked) transaction. DAO calls made inside [block] on the same
 * coroutine join this transaction.
 */
suspend fun <T> KleanDatabase.transact(block: suspend () -> T): T =
    useWriterConnection { it.immediateTransaction { block() } }
