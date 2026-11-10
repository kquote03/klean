package app.klean.feature.contacts

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.shared.module.ModuleId
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class FakeClock(var nowMs: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}

val Dubai: TimeZone = TimeZone.of("Asia/Dubai")

object DubaiZone : ZoneProvider {
    override fun current(): TimeZone = Dubai
}

/** Epoch ms of a Dubai wall-clock time. */
fun dubai(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
    LocalDateTime(y, mo, d, h, mi).toInstant(Dubai).toEpochMilliseconds()

fun date(y: Int, mo: Int, d: Int) = LocalDate(y, mo, d)

const val HOUR = 3_600_000L
const val DAY = 86_400_000L

fun inMemoryDb(): KleanDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

fun tempDevicePrefs(clock: Clock, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): DevicePrefs {
    val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
    return DevicePrefs(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock)
}

class FakeModuleGate(off: Set<ModuleId> = emptySet()) : ModuleGate {
    val off = MutableStateFlow(off)
    override val disabledIds: Flow<Set<ModuleId>> = this.off
    override suspend fun isEnabled(id: ModuleId) = id !in off.value
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
        off.value = if (enabled) off.value - id else off.value + id
    }
}

fun sync(at: Long = 0L, deleted: Boolean = false) = SyncColumns(at, at, "test", deleted, 1)

fun contact(
    id: String,
    name: String,
    month: Int? = null,
    day: Int? = null,
    year: Int? = null,
    reminders: String = "0,3",
    phones: String = "[]",
    emails: String = "[]",
    deleted: Boolean = false,
) = app.klean.core.data.db.entity.ContactEntity(
    id = id, displayName = name, phones = phones, emails = emails, birthdayMonth = month, birthdayDay = day, birthdayYear = year,
    birthdayReminders = reminders, sync = sync(deleted = deleted),
)
