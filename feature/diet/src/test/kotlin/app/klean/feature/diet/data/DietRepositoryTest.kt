package app.klean.feature.diet.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.FoodItemState
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DietRepositoryTest {
    private val nov8 = LocalDate(2026, 11, 8)
    private val nov9 = LocalDate(2026, 11, 9)
    private val noon = atMinute(nov9, 12 * 60, Dubai)
    private val clock = FakeClock(noon)
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val changes = mutableListOf<DietChange>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var aiAvailable = false
    private val policy = object : EnrichmentPolicy {
        override suspend fun hasProviderFor(task: AiTask) = aiAvailable
    }
    private val repo = DefaultDietRepository(
        db, stamper, clock, DubaiZone, settings,
        DietChangeDispatcher(setOf(DietChangeListener { synchronized(changes) { changes += it } })),
        policy, scope,
    )

    @After fun close() {
        scope.cancel()
        db.close()
    }

    @Test fun logEntryStoresTrimmedTextAndNotifiesOnce() = runBlocking {
        val id = repo.logEntry("  shawarma and fries \n", CaptureSource.MODULE_SCREEN)
        val e = db.foodEntryDao().get(id)!!
        assertEquals("shawarma and fries", e.rawText)
        assertEquals(CaptureSource.MODULE_SCREEN, e.source)
        assertEquals(noon, e.eatenAt)
        assertEquals("Asia/Dubai", e.eatenAtZone)
        assertEquals(EnrichmentStatus.NOT_REQUESTED, e.enrichment)
        assertEquals(listOf<DietChange>(DietChange.Logged(id, "shawarma and fries")), changes)
    }

    @Test fun logEntryMarksPendingWhenAProviderIsAssigned() = runBlocking {
        aiAvailable = true
        val id = repo.logEntry("toast", CaptureSource.WIDGET)
        assertEquals(EnrichmentStatus.PENDING, db.foodEntryDao().get(id)!!.enrichment)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankTextThrows() {
        runBlocking { repo.logEntry("   ", CaptureSource.MODULE_SCREEN) }
    }

    @Test fun editEatenAtChangesOnlyTimeAndAdvancesChangeSeq() = runBlocking {
        val id = repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        val before = db.foodEntryDao().get(id)!!
        val at14 = atMinute(nov9, 14 * 60, Dubai)
        clock.nowMs += 60_000
        repo.edit(id, eatenAt = at14)
        val after = db.foodEntryDao().get(id)!!
        assertEquals(at14, after.eatenAt)
        assertEquals("toast", after.rawText)
        assertTrue(after.sync.changeSeq > before.sync.changeSeq)
        assertEquals(before.sync.createdAt, after.sync.createdAt)
        assertEquals(before.copy(eatenAt = at14, sync = after.sync), after)
        assertEquals(DietChange.Edited(id), changes.last())
    }

    @Test fun editTextRequestsEnrichmentAgainWhenAProviderIsAssigned() = runBlocking {
        val id = repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        aiAvailable = true
        repo.edit(id, text = " toast with jam ")
        val e = db.foodEntryDao().get(id)!!
        assertEquals("toast with jam", e.rawText)
        assertEquals(EnrichmentStatus.PENDING, e.enrichment)
    }

    @Test fun deleteTombstonesAndHidesFromObserveDay() = runBlocking {
        val id = repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        repo.observeDay(nov9).test {
            assertEquals(listOf(id), awaitItem().map { it.id })
            repo.delete(id)
            assertEquals(emptyList<String>(), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        val row = db.foodEntryDao().get(id)
        assertNotNull(row)
        assertTrue(row!!.sync.deleted)
        assertEquals(DietChange.Deleted(id), changes.last())
    }

    @Test fun restoreUndoesDeleteIncludingItems() = runBlocking {
        val id = repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        val seq = db.foodEntryDao().get(id)!!.sync
        db.foodItemDao().upsertAll(
            listOf(
                FoodItemEntity("i1", id, 0, "toast", null, null, "gemini", null, FoodItemState.ACCEPTED, seq),
                FoodItemEntity("i0", id, 1, "old", null, null, "gemini", null, FoodItemState.ACCEPTED, seq.copy(deleted = true)),
            ),
        )
        clock.nowMs += 1_000
        repo.delete(id)
        assertTrue(db.foodItemDao().forEntry(id).all { it.sync.deleted })
        clock.nowMs += 1_000
        repo.restore(id)
        assertEquals(false, db.foodEntryDao().get(id)!!.sync.deleted)
        assertEquals(listOf("i1"), db.foodItemDao().forEntry(id).filterNot { it.sync.deleted }.map { it.id })
        assertEquals(listOf(id), repo.today().map { it.id })
        assertEquals(DietChange.Edited(id), changes.last())
    }

    @Test fun observeDayUsesLogicalDay() = runBlocking {
        // Sleep 00:00-08:00 puts the day boundary at 04:00, so 02:00 on Nov 9 belongs to Nov 8.
        val lateSnack = repo.logEntry("late snack", CaptureSource.MODULE_SCREEN, eatenAt = atMinute(nov9, 2 * 60, Dubai))
        val lunch = repo.logEntry("lunch", CaptureSource.MODULE_SCREEN)
        repo.observeDay(nov8).test {
            assertEquals(listOf(lateSnack), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(lunch), repo.today().map { it.id })
        assertEquals(nov9, repo.todayDate())
        assertEquals(TodaySummary(1, "lunch", noon), repo.todaySummary())
    }

    @Test fun quickRepeatsRankFrequentTexts() = runBlocking {
        repeat(3) { repo.logEntry("coffee", CaptureSource.MODULE_SCREEN) }
        repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        assertEquals(listOf("coffee", "toast"), repo.quickRepeats())
    }

    @Test fun eatenTimesInRangeSkipTombstones() = runBlocking {
        val a = repo.logEntry("tea", CaptureSource.MODULE_SCREEN, eatenAt = noon - 3_600_000)
        val b = repo.logEntry("toast", CaptureSource.MODULE_SCREEN, eatenAt = noon - 60_000)
        repo.logEntry("later", CaptureSource.MODULE_SCREEN, eatenAt = noon + 3_600_000)
        repo.delete(b)
        repo.observeEatenTimes(noon - 7_200_000, noon).test {
            assertEquals(listOf(noon - 3_600_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(a)
    }
}
