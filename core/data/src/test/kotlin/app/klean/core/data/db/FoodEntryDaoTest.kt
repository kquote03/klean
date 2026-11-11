package app.klean.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoodEntryDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()

    @After fun close() = db.close()

    private suspend fun put(id: String, at: Long, kcal: Int?, source: String?, deleted: Boolean = false) =
        db.foodEntryDao().upsert(
            FoodEntryEntity(
                id = id, rawText = id, eatenAt = at, eatenAtZone = "Asia/Dubai", photoFile = null,
                source = CaptureSource.MODULE_SCREEN, viaVoice = false, mealType = null, mealTypeProvider = null,
                enrichment = EnrichmentStatus.NOT_REQUESTED, kcal = kcal, kcalSource = source,
                sync = SyncColumns(at, at, "dev", deleted, at),
            ),
        )

    @Test fun withCaloriesSinceKeepsOwnAndAiNewestFirst() = runBlocking {
        put("old", 50, 100, "USER")
        put("user", 100, 300, "USER")
        put("ai", 200, 105, "AI")
        put("hist", 300, 110, "HISTORY")
        put("none", 400, null, null)
        put("gone", 500, 90, "USER", deleted = true)
        assertEquals(listOf("ai", "user"), db.foodEntryDao().withCaloriesSince(100).map { it.id })
    }
}
