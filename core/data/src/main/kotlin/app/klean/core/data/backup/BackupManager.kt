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
import java.io.IOException
import java.io.InputStream
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
) : BackupOperations {
    /** Writes a complete backup to [out]; the caller owns and closes the stream. */
    override suspend fun exportTo(out: OutputStream, includeSecrets: Boolean?): Unit = withContext(Dispatchers.IO) {
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

    /** Entries currently in Klean (not deleted), for the restore confirmation. */
    override suspend fun currentEntryCount(): Int = db.foodEntryDao().count()

    /** Validates a backup and reads its summary. Never touches live data. */
    override suspend fun inspect(input: InputStream): BackupInfo = withContext(Dispatchers.IO) {
        val cand = File(ctx.cacheDir, "restore-inspect.db")
        try {
            copyToCache(input, cand.name)
            validate(cand)
        } catch (e: IOException) {
            throw BackupInvalid(BackupInvalid.Reason.DAMAGED, e)
        } finally {
            deleteDbFiles(cand)
        }
    }

    /**
     * Replaces the database, photos and device prefs (and secrets, if the backup has them) with the
     * backup's. Everything that can fail is checked before the live data is touched. Closes the live
     * database, so the caller must restart the process afterwards ([ProcessRestarter]).
     * Throws [BackupInvalid] for anything that fails before live data is touched; any other exception
     * means the swap was interrupted and the process must restart too.
     */
    override suspend fun restore(input: InputStream): Unit = withContext(Dispatchers.IO) {
        val cand = File(ctx.cacheDir, "restore-candidate.db")
        val staging = File(ctx.filesDir, "photos-restore")
        val devicePrefs = mutableMapOf<String, String>()
        var secretMap: Map<String, String>? = null
        try {
            copyToCache(input, cand.name)
            validate(cand)
            staging.deleteRecursively()
            staging.mkdirs()
            BundledSQLiteDriver().open(cand.absolutePath).use { c ->
                c.prepare("SELECT file_name, bytes FROM $PHOTO").use { s ->
                    while (s.step()) File(staging, safePhotoName(s.getText(0))).writeBytes(s.getBlob(1))
                }
                devicePrefs += pairs(c, DEVICE_PREF)
                if (SECRET in tableNames(c)) secretMap = pairs(c, SECRET)
                BACKUP_TABLES.forEach { c.execSQL("DROP TABLE IF EXISTS $it") }
                c.execSQL("VACUUM") // drop the photo blobs' pages from the restored database
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            deleteDbFiles(cand)
            throw e as? BackupInvalid ?: BackupInvalid(BackupInvalid.Reason.DAMAGED, e)
        }

        // Point of no return: swap the database file and the photos directory.
        db.close()
        deleteDbFiles(dbFile)
        dbFile.parentFile?.mkdirs()
        cand.copyTo(dbFile, overwrite = true)
        deleteDbFiles(cand)
        val live = photos.photosDir
        live.deleteRecursively()
        if (!staging.renameTo(live)) {
            staging.copyRecursively(live, overwrite = true)
            staging.deleteRecursively()
        }
        prefs.importAll(devicePrefs)
        secretMap?.let { secrets.importPlain(it) }
    }

    private fun validate(file: File): BackupInfo {
        val invalid = { r: BackupInvalid.Reason -> BackupInvalid(r) }
        try {
            BundledSQLiteDriver().open(file.absolutePath).use { c ->
                val tables = try {
                    tableNames(c)
                } catch (e: Exception) {
                    throw BackupInvalid(BackupInvalid.Reason.NOT_A_BACKUP, e) // "file is not a database"
                }
                if (META !in tables) throw invalid(BackupInvalid.Reason.NOT_A_BACKUP)
                val meta = pairs(c, META)
                if (meta["format"] != FORMAT) throw invalid(BackupInvalid.Reason.NOT_A_BACKUP)
                val version = c.prepare("PRAGMA user_version").use { s -> s.step(); s.getLong(0).toInt() }
                if (version > KleanDatabase.VERSION) throw invalid(BackupInvalid.Reason.NEWER_VERSION)
                if (version < 1 || listOf("food_entry", PHOTO, DEVICE_PREF).any { it !in tables }) {
                    throw invalid(BackupInvalid.Reason.DAMAGED)
                }
                val check = c.prepare("PRAGMA quick_check").use { s -> s.step(); s.getText(0) }
                if (check != "ok") throw invalid(BackupInvalid.Reason.DAMAGED)
                return BackupInfo(
                    exportedAt = meta["exportedAt"]?.toLongOrNull() ?: throw invalid(BackupInvalid.Reason.DAMAGED),
                    schemaVersion = version,
                    appVersion = meta["appVersion"].orEmpty(),
                    entryCount = count(c, "SELECT COUNT(*) FROM food_entry WHERE deleted = 0"),
                    photoCount = count(c, "SELECT COUNT(*) FROM $PHOTO"),
                    hasSecrets = SECRET in tables,
                )
            }
        } catch (e: BackupInvalid) {
            throw e
        } catch (e: Exception) {
            throw BackupInvalid(BackupInvalid.Reason.DAMAGED, e)
        }
    }

    private fun copyToCache(input: InputStream, name: String): File {
        val f = File(ctx.cacheDir, name)
        deleteDbFiles(f)
        f.outputStream().use { input.copyTo(it) }
        return f
    }

    private fun deleteDbFiles(f: File) = listOf("", "-wal", "-shm", "-journal").forEach { File(f.path + it).delete() }

    /** Photo names are plain file names; anything that could escape the photos dir means a bad file. */
    private fun safePhotoName(name: String): String {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name || '\u0000' in name) {
            throw BackupInvalid(BackupInvalid.Reason.DAMAGED)
        }
        return name
    }

    private fun tableNames(c: SQLiteConnection): Set<String> =
        c.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").use { s ->
            buildSet { while (s.step()) add(s.getText(0)) }
        }

    private fun pairs(c: SQLiteConnection, table: String): Map<String, String> =
        c.prepare("SELECT key, value FROM $table").use { s ->
            buildMap { while (s.step()) put(s.getText(0), s.getText(1)) }
        }

    private fun count(c: SQLiteConnection, sql: String): Int = c.prepare(sql).use { s -> s.step(); s.getLong(0).toInt() }

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
