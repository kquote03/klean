package app.klean.feature.diet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.calories.CalorieFill
import app.klean.feature.diet.calories.FillResult
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.atMinute
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Calories ride along when logging: a token at the end of the text, or the slot (which wins). */
@RunWith(AndroidJUnit4::class)
class LogCaloriesTest {
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 11), 12 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fillRequests = Collections.synchronizedList(mutableListOf<String>())
    private val repo = DefaultDietRepository(
        db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), DietChangeDispatcher(emptySet()),
        object : EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }, scope,
        CalorieFill { fillRequests += it; FillResult.NotPossible },
    )

    @After fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun log(text: String, calories: String? = null) =
        db.foodEntryDao().get(repo.logEntry(text, CaptureSource.MODULE_SCREEN, calories = calories))!!

    @Test fun kcalAtTheEndIsTheUsersOwn() = runBlocking {
        val e = log("oatmeal 150 kcal")
        assertEquals("oatmeal", e.rawText)
        assertEquals(150, e.kcal)
        assertEquals("USER", e.kcalSource)
        assertNull(e.grams)
        assertTrue(fillRequests.isEmpty())
    }

    @Test fun gramsAtTheEndAreKeptAndCaloriesAreFilledLater() = runBlocking {
        val e = log("rice 200g")
        assertEquals("rice", e.rawText)
        assertEquals(200, e.grams)
        assertNull(e.kcal)
        assertEquals(listOf(e.id), fillRequests.toList())
    }

    @Test fun theSlotWorksOutPerAmount() = runBlocking {
        val e = log("chips", calories = "150x100x200")
        assertEquals("chips", e.rawText)
        assertEquals(300, e.kcal)
        assertEquals("USER", e.kcalSource)
        assertEquals(200, e.grams)
    }

    @Test fun theSlotWinsAndTheTokenIsStillStripped() = runBlocking {
        val e = log("chips 90 kcal", calories = "300")
        assertEquals("chips", e.rawText)
        assertEquals(300, e.kcal)
    }

    @Test fun aBareNumberStaysPartOfTheFood() = runBlocking {
        val e = log("2 eggs")
        assertEquals("2 eggs", e.rawText)
        assertNull(e.kcal)
        assertNull(e.grams)
    }

    @Test fun anUnreadableSlotIsIgnored() = runBlocking {
        val e = log("salad", calories = "lots")
        assertEquals("salad", e.rawText)
        assertNull(e.kcal)
        assertNull(e.kcalSource)
    }

    @Test fun setCaloriesParsesAndEmptyClearsAndRefills() = runBlocking {
        val id = log("chips").id
        fillRequests.clear()
        assertTrue(repo.setCalories(id, "150x100x200"))
        db.foodEntryDao().get(id)!!.let {
            assertEquals(300, it.kcal)
            assertEquals("USER", it.kcalSource)
            assertEquals(200, it.grams)
        }
        assertFalse(repo.setCalories(id, "lots"))
        assertEquals(300, db.foodEntryDao().get(id)!!.kcal)
        assertTrue(repo.setCalories(id, ""))
        db.foodEntryDao().get(id)!!.let {
            assertNull(it.kcal)
            assertNull(it.kcalSource)
            assertNull(it.grams)
        }
        assertEquals(listOf(id), fillRequests.toList())
    }
}
