package app.klean

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.backup.BackupInvalid
import app.klean.core.data.backup.BackupManager
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.photo.PhotoStore
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.data.DietRepository
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.QuietHours
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plan Task 9.3: export, wipe, restore, then reopen the file as the restarted process would. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var ctx: Context
    @Inject lateinit var backup: BackupManager
    @Inject lateinit var diet: DietRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var photos: PhotoStore
    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var db: KleanDatabase

    @Before fun inject() = hilt.inject()

    private fun generatedPhoto(): Uri {
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(180, 140, 90)) }
        val f = File(ctx.cacheDir, "roundtrip-src.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(f)
    }

    @Test fun exportWipeRestoreKeepsEverything() = runBlocking {
        photos.photosDir.deleteRecursively()
        val photo = photos.import(generatedPhoto())
        val photoBytes = photos.file(photo).readBytes()
        diet.logEntry("porridge", CaptureSource.MODULE_SCREEN, photoFile = photo)
        diet.logEntry("shawarma and fries", CaptureSource.NOTIFICATION_REPLY)
        diet.logEntry("tea", CaptureSource.WIDGET)
        settings.set(SyncedKeys.DietShowCalories, true)
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        val before = db.foodEntryDao().range(0, Long.MAX_VALUE).map { Triple(it.id, it.rawText, it.eatenAt) }
        assertEquals(3, before.size)

        val bytes = ByteArrayOutputStream().also { backup.exportTo(it) }.toByteArray()

        withContext(Dispatchers.IO) { db.clearAllTables() }
        photos.photosDir.deleteRecursively()
        assertEquals(false, settings.get(SyncedKeys.DietShowCalories))

        backup.restore(ByteArrayInputStream(bytes))

        val reopened = TestDatabaseModule.build(ctx)
        try {
            val after = reopened.foodEntryDao().range(0, Long.MAX_VALUE).map { Triple(it.id, it.rawText, it.eatenAt) }
            assertEquals(before, after)
            assertArrayEquals(photoBytes, photos.file(photo).readBytes())
            val restoredSettings = SettingsRepository(reopened, SyncStamper(reopened, prefs, Clock.System))
            assertEquals(true, restoredSettings.get(SyncedKeys.DietShowCalories))
            assertEquals(QuietHours(23 * 60, 7 * 60), restoredSettings.get(SyncedKeys.QuietHoursKey))
            val backupTables = reopened.useReaderConnection { c ->
                c.usePrepared("SELECT name FROM sqlite_master WHERE name LIKE 'backup_%'") { s ->
                    buildList { while (s.step()) add(s.getText(0)) }
                }
            }
            assertTrue(backupTables.toString(), backupTables.isEmpty())
        } finally {
            reopened.close()
        }
    }

    @Test fun newerSchemaIsRejected() = runBlocking {
        diet.logEntry("toast", CaptureSource.MODULE_SCREEN)
        val file = File(ctx.cacheDir, "newer.sqlite")
        file.outputStream().use { backup.exportTo(it) }
        BundledSQLiteDriver().open(file.absolutePath).use { it.execSQL("PRAGMA user_version = 99") }
        try {
            backup.inspect(file.inputStream())
            fail("expected BackupInvalid")
        } catch (e: BackupInvalid) {
            assertEquals(BackupInvalid.Reason.NEWER_VERSION, e.reason)
        }
    }
}
