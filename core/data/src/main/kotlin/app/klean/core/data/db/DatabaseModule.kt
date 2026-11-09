package app.klean.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): KleanDatabase =
        Room.databaseBuilder(ctx, KleanDatabase::class.java, KleanDatabase.FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides fun foodEntryDao(db: KleanDatabase) = db.foodEntryDao()
    @Provides fun foodItemDao(db: KleanDatabase) = db.foodItemDao()
    @Provides fun promptLogDao(db: KleanDatabase) = db.promptLogDao()
    @Provides fun settingDao(db: KleanDatabase) = db.settingDao()
    @Provides fun geofenceDao(db: KleanDatabase) = db.geofenceDao()
    @Provides fun changeCounterDao(db: KleanDatabase) = db.changeCounterDao()
    @Provides fun taskListDao(db: KleanDatabase) = db.taskListDao()
    @Provides fun taskDao(db: KleanDatabase) = db.taskDao()
    @Provides fun reminderDao(db: KleanDatabase) = db.reminderDao()
    @Provides fun eventDao(db: KleanDatabase) = db.eventDao()
    @Provides fun contactDao(db: KleanDatabase) = db.contactDao()
    @Provides fun workoutTemplateDao(db: KleanDatabase) = db.workoutTemplateDao()
    @Provides fun workoutSessionDao(db: KleanDatabase) = db.workoutSessionDao()
    @Provides fun workoutSetDao(db: KleanDatabase) = db.workoutSetDao()
    @Provides fun sleepDao(db: KleanDatabase) = db.sleepDao()
    @Provides fun delegationDao(db: KleanDatabase) = db.delegationDao()
    @Provides fun delegationMessageDao(db: KleanDatabase) = db.delegationMessageDao()
    @Provides fun approvalRequestDao(db: KleanDatabase) = db.approvalRequestDao()
    @Provides fun calendarSourceDao(db: KleanDatabase) = db.calendarSourceDao()
    @Provides fun exerciseDefinitionDao(db: KleanDatabase) = db.exerciseDefinitionDao()
    @Provides fun sleepScheduleDao(db: KleanDatabase) = db.sleepScheduleDao()
    @Provides fun deviceDao(db: KleanDatabase) = db.deviceDao()
    @Provides fun syncCursorDao(db: KleanDatabase) = db.syncCursorDao()
}
