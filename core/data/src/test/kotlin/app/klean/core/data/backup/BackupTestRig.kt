package app.klean.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.core.data.FakeClock
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.transact
import app.klean.core.data.photo.PhotoStore
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import java.io.File
import kotlinx.coroutines.Dispatchers

suspend fun KleanDatabase.queryStrings(sql: String): List<String> = useReaderConnection { c ->
    c.usePrepared(sql) { s -> buildList { while (s.step()) add(s.getText(0)) } }
}

class FakeSecretBackup(var stored: Map<String, String> = emptyMap()) : SecretBackup {
    val imported = mutableListOf<Map<String, String>>()
    override suspend fun exportPlain(): Map<String, String> = stored
    override suspend fun importPlain(secrets: Map<String, String>) {
        imported += secrets
        stored = secrets
    }
}

/** A file-backed database plus every collaborator BackupManager needs, wired by hand. */
class BackupTestRig(val clock: FakeClock = FakeClock(1_762_600_000_000L)) {
    val ctx: Context = ApplicationProvider.getApplicationContext()
    val dbFile: File = ctx.getDatabasePath(KleanDatabase.FILE_NAME)
    var db: KleanDatabase = openDb()
        private set
    val prefs = tempDevicePrefs(clock)
    val photos = PhotoStore(ctx, clock)
    val secrets = FakeSecretBackup()
    val stamper get() = SyncStamper(db, prefs, clock)
    val settings get() = SettingsRepository(db, stamper)
    val buildConfig = object : BuildConfigProvider {
        override val versionName = "0.1.0-test"
    }

    fun manager() = BackupManager(ctx, db, dbFile, photos, prefs, secrets, buildConfig, clock)

    fun openDb(): KleanDatabase =
        Room.databaseBuilder(ctx, KleanDatabase::class.java, dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /** Simulates the process restart after a restore: a fresh Room instance on the same file. */
    fun reopen(): KleanDatabase = openDb().also { db = it }

    suspend fun logEntry(id: String, text: String, eatenAt: Long, photo: String? = null) {
        db.transact {
            db.foodEntryDao().upsert(
                FoodEntryEntity(
                    id = id, rawText = text, eatenAt = eatenAt, eatenAtZone = "Asia/Dubai", photoFile = photo,
                    source = CaptureSource.MODULE_SCREEN, viaVoice = false, mealType = null, mealTypeProvider = null,
                    enrichment = EnrichmentStatus.NOT_REQUESTED, sync = stamper.stamp(),
                ),
            )
        }
    }
}
