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
}
