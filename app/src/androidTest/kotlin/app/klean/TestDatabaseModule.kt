package app.klean

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.klean.core.data.backup.DatabaseFile
import app.klean.core.data.backup.DatabaseFileModule
import app.klean.core.data.db.DatabaseModule
import app.klean.core.data.db.KleanDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/*
 * Shared by every instrumented test (InlineReplySaveTest, BackupRoundTripTest): Hilt allows only one
 * @TestInstallIn replacement of DatabaseModule. Each test's component gets a freshly deleted file.
 */
/** The real file database, under a test-only name so the installed app's data is never touched. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class, DatabaseFileModule::class])
object TestDatabaseModule {
    const val NAME = "klean-roundtrip-test.db"

    fun build(ctx: Context): KleanDatabase =
        Room.databaseBuilder(ctx, KleanDatabase::class.java, NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides @Singleton fun db(@ApplicationContext ctx: Context): KleanDatabase {
        listOf("", "-wal", "-shm").forEach { ctx.getDatabasePath(NAME + it).delete() }
        return build(ctx)
    }

    @Provides @DatabaseFile fun dbFile(@ApplicationContext ctx: Context): File = ctx.getDatabasePath(NAME)
    @Provides fun foodEntryDao(db: KleanDatabase) = db.foodEntryDao()
    @Provides fun foodItemDao(db: KleanDatabase) = db.foodItemDao()
    @Provides fun promptLogDao(db: KleanDatabase) = db.promptLogDao()
    @Provides fun settingDao(db: KleanDatabase) = db.settingDao()
    @Provides fun geofenceDao(db: KleanDatabase) = db.geofenceDao()
    @Provides fun changeCounterDao(db: KleanDatabase) = db.changeCounterDao()
}
