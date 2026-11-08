package app.klean.core.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.KleanDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupExportTest {
    private val rig = BackupTestRig()

    @After fun close() = rig.db.close()

    private fun ByteArray.toTempDb(): File =
        File.createTempFile("exported", ".sqlite", rig.ctx.cacheDir).also { it.writeBytes(this) }

    private fun query(file: File, sql: String): List<List<String?>> =
        BundledSQLiteDriver().open(file.absolutePath).use { c ->
            c.prepare(sql).use { s ->
                buildList {
                    while (s.step()) add((0 until s.getColumnCount()).map { if (s.isNull(it)) null else s.getText(it) })
                }
            }
        }

    @Test fun exportIsOneSqliteFileWithDataPhotosPrefsAndMeta() = runBlocking {
        val photoBytes = ByteArray(2048) { (it * 7).toByte() }
        rig.photos.file("p1.jpg").writeBytes(photoBytes)
        rig.logEntry("e1", "toast", 1_000L, photo = "p1.jpg")
        rig.logEntry("e2", "tea", 2_000L)
        rig.prefs.setDynamicColor(false)
        rig.prefs.setAutoBackupKeep(3)
        rig.prefs.setAutoBackupTreeUri("content://tree/x")
        rig.prefs.deviceId()

        val out = ByteArrayOutputStream()
        rig.manager().exportTo(out, includeSecrets = false)
        val file = out.toByteArray().toTempDb()

        assertEquals(listOf(listOf("e1", "toast"), listOf("e2", "tea")), query(file, "SELECT id, raw_text FROM food_entry ORDER BY id"))
        val meta = query(file, "SELECT key, value FROM backup_meta").associate { it[0] to it[1] }
        assertEquals("klean-backup-v1", meta["format"])
        assertEquals(rig.clock.nowMs.toString(), meta["exportedAt"])
        assertEquals("0.1.0-test", meta["appVersion"])
        assertEquals(KleanDatabase.VERSION.toString(), meta["schemaVersion"])
        assertEquals(KleanDatabase.VERSION.toString(), query(file, "PRAGMA user_version")[0][0])

        val blob = BundledSQLiteDriver().open(file.absolutePath).use { c ->
            c.prepare("SELECT bytes FROM backup_photo WHERE file_name = 'p1.jpg'").use { s -> s.step(); s.getBlob(0) }
        }
        assertArrayEquals(photoBytes, blob)

        val prefs = query(file, "SELECT key, value FROM backup_device_pref").associate { it[0] to it[1] }
        assertEquals("false", prefs["dynamicColor"])
        assertEquals("3", prefs["autoBackupKeep"])
        assertFalse("deviceId is per install", "deviceId" in prefs)
        assertFalse("folder grants are per device", "autoBackupTreeUri" in prefs)
        assertTrue(query(file, "SELECT name FROM sqlite_master WHERE name = 'backup_secret'").isEmpty())
        assertTrue("no temp file left behind", rig.ctx.cacheDir.listFiles()!!.none { it.name.startsWith("export-") })
    }

    @Test fun secretsAreIncludedOnlyWhenAsked() = runBlocking {
        rig.secrets.stored = mapOf("gemini.apiKey" to "k1")
        val out = ByteArrayOutputStream()
        rig.manager().exportTo(out, includeSecrets = true)
        val file = out.toByteArray().toTempDb()
        assertEquals(listOf(listOf("gemini.apiKey", "k1")), query(file, "SELECT key, value FROM backup_secret"))
    }

    @Test fun defaultFollowsIncludeSecretsPref() = runBlocking {
        rig.secrets.stored = mapOf("openai.apiKey" to "k2")
        rig.prefs.setBackupIncludeSecrets(true)
        val out = ByteArrayOutputStream()
        rig.manager().exportTo(out)
        assertEquals(1, query(out.toByteArray().toTempDb(), "SELECT key FROM backup_secret").size)
    }

    @Test fun livePhotoDirIsNotTouchedAndUnfinishedTmpFilesAreSkipped() = runBlocking {
        rig.photos.file("a.jpg").writeBytes(byteArrayOf(1))
        rig.photos.file("b.jpg.tmp").writeBytes(byteArrayOf(2))
        val out = ByteArrayOutputStream()
        rig.manager().exportTo(out, includeSecrets = false)
        assertEquals(listOf(listOf("a.jpg")), query(out.toByteArray().toTempDb(), "SELECT file_name FROM backup_photo"))
        assertTrue(rig.photos.file("a.jpg").exists())
    }

    @Test fun exportedDbStaysWritableAfterwards() = runBlocking {
        rig.manager().exportTo(ByteArrayOutputStream(), includeSecrets = false)
        rig.logEntry("e3", "rice", 3_000L)
        assertEquals(1, rig.db.foodEntryDao().count())
    }
}
