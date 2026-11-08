package app.klean.core.data.backup

import android.content.Context
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.photo.PhotoStore
import app.klean.core.data.prefs.DevicePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Backup is one SQLite file: an atomic `VACUUM INTO` copy of the live database plus `backup_*` tables
 * holding photos, device prefs, metadata and (if the user opts in) secrets. Restoring drops those
 * tables again, so the restored file is exactly a Klean database.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: KleanDatabase,
    @DatabaseFile private val dbFile: File,
    private val photos: PhotoStore,
    private val prefs: DevicePrefs,
    private val secrets: SecretBackup,
    private val buildConfig: BuildConfigProvider,
    private val clock: Clock,
) {
    /** Writes a complete backup to [out]; the caller owns and closes the stream. */
    suspend fun exportTo(out: OutputStream, includeSecrets: Boolean? = null) = withContext(Dispatchers.IO) {
        val withSecrets = includeSecrets ?: prefs.backupIncludeSecrets.first()
        val now = clock.now().toEpochMilliseconds()
        val tmp = File(ctx.cacheDir, "export-$now.db").apply { delete() }
        try {
            vacuumInto(tmp)
            BundledSQLiteDriver().open(tmp.absolutePath).use { c ->
                c.execSQL("CREATE TABLE $META(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
                c.execSQL("CREATE TABLE $PHOTO(file_name TEXT PRIMARY KEY NOT NULL, bytes BLOB NOT NULL)")
                c.execSQL("CREATE TABLE $DEVICE_PREF(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
                c.execSQL("BEGIN")
                insertPair(c, META, "format", FORMAT)
                insertPair(c, META, "exportedAt", now.toString())
                insertPair(c, META, "appVersion", buildConfig.versionName)
                insertPair(c, META, "schemaVersion", KleanDatabase.VERSION.toString())
                photos.photosDir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".tmp") }
                    ?.sortedBy { it.name }
                    ?.forEach { f ->
                        c.prepare("INSERT INTO $PHOTO VALUES (?, ?)").use { s ->
                            s.bindText(1, f.name)
                            s.bindBlob(2, f.readBytes())
                            s.step()
                        }
                    }
                prefs.exportable().forEach { (k, v) -> insertPair(c, DEVICE_PREF, k, v) }
                if (withSecrets) {
                    c.execSQL("CREATE TABLE $SECRET(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
                    secrets.exportPlain().forEach { (k, v) -> insertPair(c, SECRET, k, v) }
                }
                c.execSQL("COMMIT")
            }
            tmp.inputStream().use { it.copyTo(out) }
            out.flush()
        } finally {
            tmp.delete()
            File(tmp.path + "-journal").delete()
        }
    }

    /** Atomic, consistent copy of the live database (WAL included) taken on Room's writer connection. */
    private suspend fun vacuumInto(target: File) {
        db.useWriterConnection { c ->
            c.usePrepared("VACUUM INTO ?") { s ->
                s.bindText(1, target.absolutePath)
                s.step()
            }
        }
    }

    private fun insertPair(c: SQLiteConnection, table: String, key: String, value: String) {
        c.prepare("INSERT INTO $table VALUES (?, ?)").use { s ->
            s.bindText(1, key)
            s.bindText(2, value)
            s.step()
        }
    }

    companion object {
        const val FORMAT = "klean-backup-v1"
        const val META = "backup_meta"
        const val PHOTO = "backup_photo"
        const val DEVICE_PREF = "backup_device_pref"
        const val SECRET = "backup_secret"
        val BACKUP_TABLES = listOf(META, PHOTO, DEVICE_PREF, SECRET)
    }
}
