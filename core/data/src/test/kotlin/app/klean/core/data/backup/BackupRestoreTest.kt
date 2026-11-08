package app.klean.core.data.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.settings.SyncedKeys
import app.klean.shared.time.QuietHours
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Robolectric stand-in for the instrumented BackupRoundTripTest: export, wipe, restore the files,
 * then open a new Room instance on the same file as the restarted process would.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val rig = BackupTestRig()
    private val photoBytes = ByteArray(4096) { (it * 31 + 7).toByte() }

    @After fun close() = rig.db.close()

    private suspend fun seedAndExport(includeSecrets: Boolean = true): ByteArray {
        rig.photos.file("p1.jpg").writeBytes(photoBytes)
        rig.logEntry("e1", "porridge", 1_000L, photo = "p1.jpg")
        rig.logEntry("e2", "shawarma and fries", 2_000L)
        rig.logEntry("e3", "tea", 3_000L)
        rig.settings.set(SyncedKeys.DietShowCalories, true)
        rig.settings.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        rig.prefs.setDynamicColor(false)
        rig.secrets.stored = mapOf("gemini.apiKey" to "secret-1")
        return ByteArrayOutputStream().also { rig.manager().exportTo(it, includeSecrets) }.toByteArray()
    }

    private inline fun expectInvalid(reason: BackupInvalid.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected BackupInvalid($reason)")
        } catch (e: BackupInvalid) {
            assertEquals(reason, e.reason)
        }
    }

    @Test fun inspectReadsCountsWithoutTouchingLiveData() = runBlocking {
        val bytes = seedAndExport()
        rig.logEntry("e4", "late snack", 4_000L)
        val info = rig.manager().inspect(ByteArrayInputStream(bytes))
        assertEquals(BackupInfo(rig.clock.nowMs, KleanDatabase.VERSION, "0.1.0-test", 3, 1, true), info)
        assertEquals(4, rig.manager().currentEntryCount())
    }

    @Test fun roundTripRestoresEntriesPhotosSettingsPrefsAndSecrets() = runBlocking {
        val bytes = seedAndExport()
        val before = rig.db.foodEntryDao().range(0, Long.MAX_VALUE)

        // Wipe: data, photos, settings, prefs, secrets. Add things the restore must remove.
        withContext(Dispatchers.IO) { rig.db.clearAllTables() }
        rig.photos.photosDir.deleteRecursively()
        rig.photos.file("stray.jpg").writeBytes(byteArrayOf(9))
        rig.logEntry("e9", "not in the backup", 9_000L)
        rig.prefs.setDynamicColor(true)
        rig.secrets.stored = emptyMap()

        rig.manager().restore(ByteArrayInputStream(bytes))
        assertTrue("restore closes the live database", runCatching { rig.db.foodEntryDao().count() }.isFailure)

        val db = rig.reopen()
        val after = db.foodEntryDao().range(0, Long.MAX_VALUE)
        assertEquals(before.map { Triple(it.id, it.rawText, it.eatenAt) }, after.map { Triple(it.id, it.rawText, it.eatenAt) })
        assertEquals(before, after)
        assertArrayEquals(photoBytes, rig.photos.file("p1.jpg").readBytes())
        assertFalse(rig.photos.file("stray.jpg").exists())
        assertEquals(listOf("p1.jpg"), rig.photos.photosDir.list()!!.toList())
        assertEquals(true, rig.settings.get(SyncedKeys.DietShowCalories))
        assertEquals(QuietHours(23 * 60, 7 * 60), rig.settings.get(SyncedKeys.QuietHoursKey))
        assertFalse(rig.prefs.dynamicColor.first())
        assertEquals(listOf(mapOf("gemini.apiKey" to "secret-1")), rig.secrets.imported)

        val backupTables = db.queryStrings("SELECT name FROM sqlite_master WHERE name LIKE 'backup_%'")
        assertEquals(emptyList<String>(), backupTables)
        assertTrue(rig.ctx.cacheDir.listFiles()!!.none { it.name.startsWith("restore-") })
        assertFalse(File(rig.ctx.filesDir, "photos-restore").exists())
    }

    @Test fun backupWithoutSecretsLeavesSecretsAlone() = runBlocking {
        val bytes = seedAndExport(includeSecrets = false)
        assertFalse(rig.manager().inspect(ByteArrayInputStream(bytes)).hasSecrets)
        rig.manager().restore(ByteArrayInputStream(bytes))
        assertEquals(emptyList<Map<String, String>>(), rig.secrets.imported)
        assertEquals(3, rig.reopen().foodEntryDao().count())
    }

    @Test fun newerSchemaIsRejected() = runBlocking {
        val file = File(rig.ctx.cacheDir, "newer.sqlite").apply { writeBytes(seedAndExport()) }
        BundledSQLiteDriver().open(file.absolutePath).use { it.execSQL("PRAGMA user_version = 99") }
        expectInvalid(BackupInvalid.Reason.NEWER_VERSION) { rig.manager().inspect(file.inputStream()) }
        expectInvalid(BackupInvalid.Reason.NEWER_VERSION) { rig.manager().restore(file.inputStream()) }
        assertEquals("a rejected restore leaves the live database alone", 3, rig.db.foodEntryDao().count())
    }

    @Test fun nonBackupFilesAreRejected() = runBlocking {
        expectInvalid(BackupInvalid.Reason.NOT_A_BACKUP) {
            rig.manager().inspect(ByteArrayInputStream(ByteArray(5000) { it.toByte() }))
        }
        expectInvalid(BackupInvalid.Reason.NOT_A_BACKUP) { rig.manager().inspect(ByteArrayInputStream(ByteArray(0))) }
        val plain = File(rig.ctx.cacheDir, "plain.sqlite")
        BundledSQLiteDriver().open(plain.absolutePath).use { it.execSQL("CREATE TABLE t(x)") }
        expectInvalid(BackupInvalid.Reason.NOT_A_BACKUP) { rig.manager().inspect(plain.inputStream()) }
    }

    @Test fun photoNamesThatEscapeThePhotoDirAreRejected() = runBlocking {
        val file = File(rig.ctx.cacheDir, "evil.sqlite").apply { writeBytes(seedAndExport()) }
        BundledSQLiteDriver().open(file.absolutePath).use { it.execSQL("INSERT INTO backup_photo VALUES ('../evil.jpg', x'00')") }
        expectInvalid(BackupInvalid.Reason.DAMAGED) { rig.manager().restore(file.inputStream()) }
        assertEquals(3, rig.db.foodEntryDao().count())
        assertFalse(File(rig.ctx.filesDir, "evil.jpg").exists())
        assertTrue(rig.photos.file("p1.jpg").exists())
    }

    /** A v1 backup committed to the repo keeps restoring after future schema changes. */
    @Test fun committedV1FixtureStillRestores() = runBlocking {
        val fixture = javaClass.getResourceAsStream("/backup-v1.sqlite")
        assertNotNull("backup-v1.sqlite fixture is on the test classpath", fixture)
        val bytes = fixture!!.use { it.readBytes() }
        val info = rig.manager().inspect(ByteArrayInputStream(bytes))
        assertEquals(1, info.schemaVersion)
        assertEquals(3, info.entryCount)
        assertEquals(1, info.photoCount)
        rig.manager().restore(ByteArrayInputStream(bytes))
        val db = rig.reopen()
        assertEquals(listOf("porridge", "shawarma and fries", "tea"), db.foodEntryDao().range(0, Long.MAX_VALUE).map { it.rawText })
        assertEquals(true, rig.settings.get(SyncedKeys.DietShowCalories))
        assertEquals(4096, rig.photos.file("p1.jpg").length())
    }

    /**
     * Regenerates the committed fixture. Run with
     * `KLEAN_WRITE_BACKUP_FIXTURE=$PWD/core/data/src/test/resources/backup-v1.sqlite`.
     */
    @Test fun writeFixtureWhenAsked() = runBlocking {
        val target = System.getenv("KLEAN_WRITE_BACKUP_FIXTURE") ?: return@runBlocking
        File(target).writeBytes(seedAndExport(includeSecrets = false))
    }
}
