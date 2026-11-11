package app.klean.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 3→4 adds `food_entry.kcal`, `kcal_source` and `grams`. The v2 fixture's food entry passes through
 * 2→3→4 and reads back with no calories; new rows keep what they are given.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV4Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: KleanDatabase

    private fun open(): KleanDatabase {
        val file = File(ctx.cacheDir, "migration-v4-test.db").apply {
            listOf("", "-wal", "-shm").forEach { File(path + it).delete() }
            writeBytes(MigrationV4Test::class.java.getResourceAsStream("/backup-v2.sqlite")!!.use { it.readBytes() })
        }
        return Room.databaseBuilder(ctx, KleanDatabase::class.java, file.path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .also { db = it }
    }

    @After fun close() = db.close()

    @Test fun oldEntriesHaveNoCaloriesAndNewOnesKeepThem() = runBlocking {
        open()
        val v = db.useReaderConnection { c -> c.usePrepared("PRAGMA user_version") { s -> s.step(); s.getLong(0) } }
        assertEquals(4L, v)
        val old = db.foodEntryDao().range(0, Long.MAX_VALUE).single()
        assertEquals("porridge", old.rawText)
        assertNull(old.kcal)
        assertNull(old.kcalSource)
        assertNull(old.grams)

        db.foodEntryDao().upsert(old.copy(id = "new", kcal = 300, kcalSource = "USER", grams = 200))
        val fresh = db.foodEntryDao().get("new")!!
        assertEquals(300, fresh.kcal)
        assertEquals("USER", fresh.kcalSource)
        assertEquals(200, fresh.grams)
    }
}
