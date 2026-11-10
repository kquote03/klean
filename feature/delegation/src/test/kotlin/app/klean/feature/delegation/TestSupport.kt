package app.klean.feature.delegation

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import app.klean.feature.delegation.agent.HermesConfig
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone

class FakeClock(var nowMs: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}

object DubaiZone : ZoneProvider {
    override fun current(): TimeZone = TimeZone.of("Asia/Dubai")
}

fun inMemoryDb(): KleanDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

fun tempDevicePrefs(clock: Clock, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): DevicePrefs {
    val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
    return DevicePrefs(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock)
}

fun tempSettingsStore(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(createTempDirectory("s").toFile(), "s.preferences_pb") })

class StaticHermes(private val url: String? = "http://hermes.test", private val token: String? = "k") : HermesConfig {
    override suspend fun url() = url
    override suspend fun token() = token
}
