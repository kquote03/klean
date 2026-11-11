package app.klean.feature.diet.calories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.klean.core.ai.AiOutcome
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.ai.FakeAiProvider
import app.klean.feature.diet.ai.FakeAiService
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.KcalSource
import app.klean.shared.time.atMinute
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalorieFillerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 11), 12 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val ai = FakeAiService()
    private val queued = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
    private val dispatcher = DietChangeDispatcher(emptySet())
    private var parseAssigned = false
    private val filler = CalorieFiller(db, stamper, ai, clock, { id, net -> queued += id to net }, dispatcher)

    // The repository without automatic filling, so each test drives [filler] itself.
    private val repo = DefaultDietRepository(
        db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), dispatcher,
        object : app.klean.core.ai.EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = parseAssigned }, scope,
    )

    @After fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun log(text: String, calories: String? = null): String {
        val id = repo.logEntry(text, CaptureSource.MODULE_SCREEN, calories = calories)
        clock.nowMs += 60_000
        return id
    }

    private fun worker(entryId: String, attempt: Int = 0) =
        TestListenableWorkerBuilder<CalorieFillWorker>(ctx, CalorieFillWorker.input(entryId))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    CalorieFillWorker(appContext, workerParameters, db, stamper, ai, dispatcher)
            })
            .build()

    private fun answer(kcal: Int) = AiOutcome.Success(
        Json.parseToJsonElement("""{"items":[{"name":"rice","calories":$kcal}]}"""), ProviderId.GEMINI, "flash",
    )

    @Test fun historyFillsWithoutAskingTheAi() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        log("oatmeal", calories = "300")
        val id = log("Oatmeal")
        assertEquals(FillResult.Filled(KcalSource.HISTORY), filler.fill(id))
        db.foodEntryDao().get(id)!!.let {
            assertEquals(300, it.kcal)
            assertEquals("HISTORY", it.kcalSource)
        }
        assertTrue(ai.requests.isEmpty())
        assertTrue(queued.isEmpty())
    }

    @Test fun noHistoryWithAnAiQueuesItAndTheWorkerAsksWithTheGrams() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        ai.answers[AiTask.CALORIE_ESTIMATE] = { answer(260) }
        val id = log("rice 200g")
        assertEquals(FillResult.QueuedForAi, filler.fill(id))
        assertEquals(listOf(id to true), queued.toList())

        assertEquals(ListenableWorker.Result.success(), worker(id).doWork())
        assertEquals("Estimate calories for: rice (200 g)", ai.requests.single().second.user)
        db.foodEntryDao().get(id)!!.let {
            assertEquals(260, it.kcal)
            assertEquals("AI", it.kcalSource)
        }
    }

    @Test fun noHistoryAndNoAiLeavesItBe() = runBlocking {
        val id = log("salad")
        assertEquals(FillResult.NotPossible, filler.fill(id))
        assertTrue(queued.isEmpty())
        assertNull(db.foodEntryDao().get(id)!!.kcal)
    }

    @Test fun theUsersOwnNumberIsNeverTouched() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        val id = log("chips", calories = "300")
        assertEquals(FillResult.NotPossible, filler.fill(id))
        assertEquals(300, db.foodEntryDao().get(id)!!.kcal)
        assertTrue(queued.isEmpty())
    }

    @Test fun anImplausibleAnswerIsIgnored() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        val id = log("rice")
        ai.answers[AiTask.CALORIE_ESTIMATE] = { answer(0) }
        worker(id).doWork()
        assertNull(db.foodEntryDao().get(id)!!.kcal)
        ai.answers[AiTask.CALORIE_ESTIMATE] = { answer(6000) }
        worker(id).doWork()
        assertNull(db.foodEntryDao().get(id)!!.kcal)
    }

    @Test fun anEditInBetweenWins() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        val id = log("rice")
        ai.answers[AiTask.CALORIE_ESTIMATE] = {
            runBlocking { repo.edit(id, text = "rice and beans") }
            answer(260)
        }
        worker(id).doWork()
        assertNull(db.foodEntryDao().get(id)!!.kcal)
    }

    @Test fun offlineRetriesThenGivesUpQuietly() = runBlocking {
        ai.assigned[AiTask.CALORIE_ESTIMATE] = FakeAiProvider(ProviderId.GEMINI)
        ai.answers[AiTask.CALORIE_ESTIMATE] = { AiOutcome.Unavailable("offline") }
        val id = log("rice")
        assertEquals(ListenableWorker.Result.retry(), worker(id).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(id, attempt = CalorieFillWorker.MAX_ATTEMPTS - 1).doWork())
    }

    @Test fun theSameProviderParsingTheEntryCoversIt() = runBlocking {
        parseAssigned = true
        val gemini = FakeAiProvider(ProviderId.GEMINI)
        ai.assigned[AiTask.ENTRY_PARSE] = gemini
        ai.assigned[AiTask.CALORIE_ESTIMATE] = gemini
        val id = log("rice")
        assertEquals(FillResult.NotPossible, filler.fill(id))
        assertTrue(queued.isEmpty())
    }

    @Test fun fillMissingGoesThroughEveryEntryWithoutCalories() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val r = DefaultDietRepository(
            db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), dispatcher,
            object : app.klean.core.ai.EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }, scope,
            CalorieFill { calls += it; FillResult.NotPossible },
        )
        r.logEntry("chips", CaptureSource.MODULE_SCREEN, calories = "300")
        val a = r.logEntry("salad", CaptureSource.MODULE_SCREEN)
        calls.clear()
        r.fillMissing(LocalDate(2026, 11, 11))
        assertEquals(listOf(a), calls.toList())
    }
}
