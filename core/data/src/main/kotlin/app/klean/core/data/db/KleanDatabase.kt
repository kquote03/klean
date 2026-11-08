package app.klean.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import app.klean.core.data.db.dao.ChangeCounterDao
import app.klean.core.data.db.dao.FoodEntryDao
import app.klean.core.data.db.dao.FoodItemDao
import app.klean.core.data.db.dao.GeofenceDao
import app.klean.core.data.db.dao.PromptLogDao
import app.klean.core.data.db.dao.SettingDao
import app.klean.core.data.db.entity.ChangeCounterEntity
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.entity.GeofenceEntity
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.entity.SettingEntity

/** One database for every module, so one file is one backup. Later modules add tables via migrations. */
@Database(
    entities = [
        FoodEntryEntity::class, FoodItemEntity::class, PromptLogEntity::class,
        SettingEntity::class, GeofenceEntity::class, ChangeCounterEntity::class,
    ],
    version = KleanDatabase.VERSION,
    exportSchema = true,
)
abstract class KleanDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun promptLogDao(): PromptLogDao
    abstract fun settingDao(): SettingDao
    abstract fun geofenceDao(): GeofenceDao
    abstract fun changeCounterDao(): ChangeCounterDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "klean.db"
    }
}

/**
 * Runs [block] in an immediate (write-locked) transaction. DAO calls made inside [block] on the same
 * coroutine join this transaction.
 */
suspend fun <T> KleanDatabase.transact(block: suspend () -> T): T =
    useWriterConnection { it.immediateTransaction { block() } }
