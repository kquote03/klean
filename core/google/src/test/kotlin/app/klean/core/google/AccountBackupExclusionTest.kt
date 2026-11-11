package app.klean.core.google

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.backup.BackupManager
import app.klean.core.data.backup.BuildConfigProvider
import app.klean.core.data.backup.SecretBackup
import app.klean.core.data.photo.PhotoStore
import app.klean.core.data.sync.SyncStamper
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Profile data stays on the phone: a backup holds neither the account entry nor its photo. */
@RunWith(AndroidJUnit4::class)
class AccountBackupExclusionTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(1_762_768_800_000)
    private val scope = tempScope()
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock, scope)
    private val state = tempGoogleState(scope)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.coroutineContext.job.cancelAndJoin()
    }

    @Test fun backupsLeaveTheAccountAndItsPhotoOut() = runBlocking {
        val auth = FakeGoogleAuth().apply { tokensByAccount["ada@gmail.com"] = "tok-a"; pickedAccount = "ada@gmail.com" }
        val profiles = FakeProfileServer().apply {
            profile("tok-a", "ada@gmail.com", "Ada Lovelace", "Ada", "https://lh3.googleusercontent.com/a/A")
            photos["https://lh3.googleusercontent.com/a/A=s192-c"] = FAKE_PNG
        }
        val sync = GoogleTasksSync(db, SyncStamper(db, prefs, clock), auth, FakeGoogleServer().client(auth), state, clock)
        val photoDir = File(ctx.filesDir, GoogleAccounts.PHOTO_DIR)
        val accounts = GoogleAccounts(auth, profiles.client(), state, sync, photoDir, clock)
        auth.connect()
        accounts.adoptGranted()
        assertTrue(photoDir.listFiles()!!.isNotEmpty())

        val photos = PhotoStore(ctx, clock)
        assertFalse("account photos live outside the backed-up photos folder", photoDir.canonicalPath.startsWith(photos.photosDir.canonicalPath))
        val secrets = object : SecretBackup {
            override suspend fun exportPlain() = emptyMap<String, String>()
            override suspend fun importPlain(secrets: Map<String, String>) {}
        }
        val build = object : BuildConfigProvider { override val versionName = "test" }
        val out = ByteArrayOutputStream()
        BackupManager(ctx, db, File(ctx.cacheDir, "unused.db"), photos, prefs, secrets, build, clock).exportTo(out, includeSecrets = true)

        val bytes = out.toByteArray()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("ada@gmail.com"))
        assertFalse(text.contains("Lovelace"))
        val file = File.createTempFile("backup", ".db", ctx.cacheDir).apply { writeBytes(bytes) }
        val photoRows = BundledSQLiteDriver().open(file.absolutePath).use { c ->
            c.prepare("SELECT COUNT(*) FROM backup_photo").use { it.step(); it.getLong(0) }
        }
        assertEquals(0L, photoRows)
        accounts.forgetAll()
    }
}
