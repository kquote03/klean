package app.klean.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The committed v1 file (a real backup) opens on the current schema through the 1→2→… AutoMigrations, data intact. */
@RunWith(AndroidJUnit4::class)
class MigrationV2Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val file = File(ctx.cacheDir, "migration-v2-test.db").apply {
        listOf("", "-wal", "-shm").forEach { File(path + it).delete() }
        writeBytes(MigrationV2Test::class.java.getResourceAsStream("/backup-v1.sqlite")!!.use { it.readBytes() })
    }
    private val db = Room.databaseBuilder(ctx, KleanDatabase::class.java, file.path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @After fun close() = db.close()

    @Test fun v1FileMigratesToV2KeepingDietRows() = runBlocking {
        assertEquals(
            listOf("porridge", "shawarma and fries", "tea"),
            db.foodEntryDao().range(0, Long.MAX_VALUE).map { it.rawText },
        )
        val version = db.useReaderConnection { c -> c.usePrepared("PRAGMA user_version") { s -> s.step(); s.getLong(0) } }
        assertEquals(KleanDatabase.VERSION.toLong(), version)
    }

    @Test fun newTablesStartEmpty() = runBlocking {
        assertTrue(db.taskListDao().observeAll().first().isEmpty())
        assertTrue(db.taskDao().observeAll().first().isEmpty())
        assertTrue(db.reminderDao().observeAll().first().isEmpty())
        assertTrue(db.eventDao().observeAll().first().isEmpty())
        assertTrue(db.contactDao().observeAll().first().isEmpty())
        assertTrue(db.workoutTemplateDao().observeAll().first().isEmpty())
        assertTrue(db.workoutSessionDao().observeAll().first().isEmpty())
        assertTrue(db.workoutSetDao().observeAll().first().isEmpty())
        assertTrue(db.sleepDao().observeAll().first().isEmpty())
    }
}
