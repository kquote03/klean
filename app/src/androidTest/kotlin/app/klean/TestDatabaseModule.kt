package app.klean

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.klean.core.data.db.DatabaseModule
import app.klean.core.data.db.KleanDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/** Instrumented tests use an in-memory database with the same bundled driver as production. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): KleanDatabase =
        Room.inMemoryDatabaseBuilder(ctx, KleanDatabase::class.java)
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
