package app.klean.feature.diet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.klean.core.data.db.DatabaseModule
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefsModule
import app.klean.core.data.prefs.DevicePrefsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import javax.inject.Singleton
import kotlin.io.path.createTempDirectory

/** Hilt Robolectric tests get an in-memory database (bundled driver)... */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides @Singleton fun db(): KleanDatabase = inMemoryDb()
    @Provides fun foodEntryDao(db: KleanDatabase) = db.foodEntryDao()
    @Provides fun foodItemDao(db: KleanDatabase) = db.foodItemDao()
    @Provides fun promptLogDao(db: KleanDatabase) = db.promptLogDao()
    @Provides fun settingDao(db: KleanDatabase) = db.settingDao()
    @Provides fun geofenceDao(db: KleanDatabase) = db.geofenceDao()
    @Provides fun changeCounterDao(db: KleanDatabase) = db.changeCounterDao()
}

/** ...and a fresh DataStore file per test, so two tests never share one active DataStore. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DevicePrefsModule::class])
object TestDevicePrefsModule {
    @Provides @Singleton @DevicePrefsStore
    fun store(): DataStore<Preferences> {
        val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }
}
