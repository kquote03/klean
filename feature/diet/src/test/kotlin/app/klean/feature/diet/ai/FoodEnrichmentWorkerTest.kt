package app.klean.feature.diet.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.AiService
import app.klean.core.ai.Capability
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.transact
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.shared.module.ModuleId
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.data.DietChangeListener
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.MealType
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class FakeAiProvider(override val id: ProviderId, override val needsNetwork: Boolean = true) : AiProvider {
    override suspend fun capabilities() = emptySet<Capability>()
    override suspend fun isConfiguredAndEnabled() = true
    override suspend fun generate(req: StructuredRequest): AiOutcome = error("use FakeAiService")
}

class FakeAiService : AiService {
    val assigned = mutableMapOf<AiTask, AiProvider>()
    val answers = mutableMapOf<AiTask, (StructuredRequest) -> AiOutcome>()
    val requests = mutableListOf<Pair<AiTask, StructuredRequest>>()
    override suspend fun hasProviderFor(task: AiTask) = task in assigned
    override suspend fun providerFor(task: AiTask) = assigned[task]
    override suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean): AiOutcome {
        if (task !in assigned) return AiOutcome.NoProvider
        requests += task to req
        return answers[task]?.invoke(req) ?: AiOutcome.NoProvider
    }
    override suspend fun runWith(p: AiProvider, req: StructuredRequest, sendImage: Boolean) = error("unused")
    override fun provider(id: ProviderId): AiProvider? = assigned.values.firstOrNull { it.id == id }
}

class FakeEnrichmentScheduler : EnrichmentScheduler {
    val enqueued = mutableListOf<Triple<String, Long, Boolean>>()
    var cancelledAll = 0
    override fun cancelAll() { cancelledAll++ }
    override fun enqueue(entryId: String, enqueuedUpdatedAt: Long, needsNetwork: Boolean, replace: Boolean) {
        enqueued += Triple(entryId, enqueuedUpdatedAt, needsNetwork)
    }
}

@RunWith(AndroidJUnit4::class)
class FoodEnrichmentWorkerTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val day = LocalDate(2026, 11, 9)
    private val lunch = atMinute(day, 13 * 60 + 5, Dubai)
    private val clock = FakeClock(lunch)
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val changes = mutableListOf<DietChange>()
    private val repo = DefaultDietRepository(
        db, stamper, clock, DubaiZone, SettingsRepository(db, stamper),
        DietChangeDispatcher(setOf(DietChangeListener { changes += it })), object : app.klean.core.ai.EnrichmentPolicy {
            override suspend fun hasProviderFor(task: AiTask) = true
        }, scope,
    )
    private val gate = SettingsModuleGate(SettingsRepository(db, stamper))
    private val ai = FakeAiService()
    private val scheduler = FakeEnrichmentScheduler()
    private val local = FakeAiProvider(ProviderId.OPENAI_COMPAT)

    private val itemsJson = Json.parseToJsonElement(
        """{"items":[{"name":"shawarma","portion":"1 wrap","calories":450},{"name":"fries","portion":"medium","calories":320.0}],"mealType":"lunch"}""",
    )

    @After fun close() {
        scope.cancel()
        db.close()
    }

    private fun worker(entryId: String, enqueuedAt: Long, attempt: Int = 0): FoodEnrichmentWorker =
        TestListenableWorkerBuilder<FoodEnrichmentWorker>(ctx, FoodEnrichmentWorker.input(entryId, enqueuedAt))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    FoodEnrichmentWorker(appContext, workerParameters, db, stamper, ai, { null }, scheduler)
            })
            .build()

    private suspend fun logged(text: String = "shawarma and fries"): Pair<String, Long> {
        val id = repo.logEntry(text, CaptureSource.MODULE_SCREEN)
        return id to db.foodEntryDao().get(id)!!.sync.updatedAt
    }

    @Test fun successWritesItemsAndDoneWithoutTouchingRawText() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Success(itemsJson, ProviderId.OPENAI_COMPAT, "llama3.2") }
        val (id, at) = logged()
        val changesBefore = changes.size

        assertEquals(ListenableWorker.Result.success(), worker(id, at).doWork())

        val e = db.foodEntryDao().get(id)!!
        assertEquals("shawarma and fries", e.rawText)
        assertEquals(EnrichmentStatus.DONE, e.enrichment)
        assertEquals(MealType.LUNCH, e.mealType)
        assertEquals("openai_compat", e.mealTypeProvider)
        val items = db.foodItemDao().forEntry(id)
        assertEquals(listOf("shawarma", "fries"), items.map { it.name })
        assertEquals(listOf("1 wrap", "medium"), items.map { it.portion })
        assertEquals(listOf(450, 320), items.map { it.calories })
        assertEquals(listOf(0, 1), items.map { it.position })
        assertTrue(items.all { it.providerId == "openai_compat" && it.model == "llama3.2" && it.state == FoodItemState.ACCEPTED })
        assertTrue(items.all { it.sync.changeSeq > 0 && !it.sync.deleted })
        val sent = ai.requests.single().second
        assertEquals("Logged at 13:05 local time: shawarma and fries", sent.user)
        assertEquals("worker writes don't re-trigger enrichment", changesBefore, changes.size)
    }

    @Test fun rerunReplacesOldAiItemsButKeepsUserEdits() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Success(itemsJson, ProviderId.OPENAI_COMPAT, "m") }
        val (id, at) = logged()
        worker(id, at).doWork()
        val first = db.foodItemDao().forEntry(id)
        db.transact { db.foodItemDao().upsertAll(listOf(first[0].copy(name = "chicken shawarma", state = FoodItemState.EDITED, sync = stamper.stamp(first[0].sync)))) }
        val at2 = db.foodEntryDao().get(id)!!.sync.updatedAt
        worker(id, at2).doWork()
        val live = db.foodItemDao().forEntry(id).filterNot { it.sync.deleted }
        assertEquals(listOf("chicken shawarma", "shawarma", "fries"), live.map { it.name })
    }

    @Test fun noProviderMarksNoProvider() = runBlocking {
        val (id, at) = logged()
        assertEquals(ListenableWorker.Result.success(), worker(id, at).doWork())
        assertEquals(EnrichmentStatus.NO_PROVIDER, db.foodEntryDao().get(id)!!.enrichment)
        assertTrue(db.foodItemDao().forEntry(id).isEmpty())
    }

    @Test fun unavailableRetriesThenFailsOnTheEighthAttempt() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Unavailable("network") }
        val (id, at) = logged()
        assertEquals(ListenableWorker.Result.retry(), worker(id, at, attempt = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(id, at, attempt = 6).doWork())
        assertEquals(EnrichmentStatus.PENDING, db.foodEntryDao().get(id)!!.enrichment)
        assertEquals(ListenableWorker.Result.success(), worker(id, at, attempt = 7).doWork())
        assertEquals(EnrichmentStatus.FAILED, db.foodEntryDao().get(id)!!.enrichment)
    }

    @Test fun failedOutcomeMarksFailed() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Failed("auth") }
        val (id, at) = logged()
        worker(id, at).doWork()
        assertEquals(EnrichmentStatus.FAILED, db.foodEntryDao().get(id)!!.enrichment)
    }

    @Test fun entryEditedAfterEnqueueIsReEnqueuedAndNothingWritten() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Success(itemsJson, ProviderId.OPENAI_COMPAT, "m") }
        val (id, at) = logged()
        clock.nowMs += 60_000
        repo.edit(id, text = "shawarma, fries and a cola")
        val newAt = db.foodEntryDao().get(id)!!.sync.updatedAt

        assertEquals(ListenableWorker.Result.success(), worker(id, at).doWork())
        assertTrue(db.foodItemDao().forEntry(id).isEmpty())
        assertEquals(listOf(Triple(id, newAt, true)), scheduler.enqueued)
    }

    @Test fun deletedEntryIsSuccessAndNothingWritten() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Success(itemsJson, ProviderId.OPENAI_COMPAT, "m") }
        val (id, at) = logged()
        repo.delete(id)
        assertEquals(ListenableWorker.Result.success(), worker(id, at).doWork())
        assertTrue(db.foodItemDao().forEntry(id).isEmpty())
        assertTrue(ai.requests.isEmpty())
        assertEquals(ListenableWorker.Result.success(), worker("missing", 0).doWork())
    }

    @Test fun differentCalorieProviderFillsCaloriesByPosition() = runBlocking {
        val gemini = FakeAiProvider(ProviderId.GEMINI)
        ai.assigned[AiTask.ENTRY_PARSE] = local
        ai.assigned[AiTask.CALORIE_ESTIMATE] = gemini
        ai.answers[AiTask.ENTRY_PARSE] = { AiOutcome.Success(itemsJson, ProviderId.OPENAI_COMPAT, "m") }
        ai.answers[AiTask.CALORIE_ESTIMATE] = {
            AiOutcome.Success(Json.parseToJsonElement("""{"items":[{"name":"shawarma","calories":610},{"name":"fries","calories":null}]}"""), ProviderId.GEMINI, "g")
        }
        val (id, at) = logged()
        worker(id, at).doWork()
        val items: List<FoodItemEntity> = db.foodItemDao().forEntry(id)
        assertEquals(listOf(610, 320), items.map { it.calories })
        assertTrue(ai.requests.last().second.user.startsWith("Estimate calories for: "))
        assertTrue(ai.requests.last().second.user.contains("shawarma (1 wrap)"))
    }

    @Test fun enqueuerQueuesLoggedAndPendingEditsOnly() = runBlocking {
        val enqueuer = EnrichmentEnqueuer(db, ai, scheduler, gate)
        val (id, at) = logged()
        enqueuer.onChange(DietChange.Logged(id, "x"))
        assertTrue("no provider → nothing queued", scheduler.enqueued.isEmpty())

        ai.assigned[AiTask.ENTRY_PARSE] = FakeAiProvider(ProviderId.ON_DEVICE, needsNetwork = false)
        enqueuer.onChange(DietChange.Logged(id, "x"))
        assertEquals(listOf(Triple(id, at, false)), scheduler.enqueued)
        enqueuer.onChange(DietChange.Deleted(id))
        enqueuer.onChange(DietChange.Refresh)
        assertEquals(1, scheduler.enqueued.size)
    }

    @Test fun nothingIsQueuedWhileDietIsOff() = runBlocking {
        ai.assigned[AiTask.ENTRY_PARSE] = local
        val (id, _) = logged()
        gate.setEnabled(ModuleId.DIET, false)
        EnrichmentEnqueuer(db, ai, scheduler, gate).onChange(DietChange.Logged(id, "x"))
        assertEquals(0, EnrichmentSweep(db, ai, scheduler, clock, gate).run())
        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test fun sweepQueuesRecentEntriesWithoutDetailsOnlyWhenAProviderIsOn() = runBlocking {
        val sweep = EnrichmentSweep(db, ai, scheduler, clock, gate)
        val (a, atA) = logged("toast")
        val (b, _) = logged("tea")
        db.transact {
            val e = db.foodEntryDao().get(b)!!
            db.foodEntryDao().upsert(e.copy(enrichment = EnrichmentStatus.DONE, sync = stamper.stamp(e.sync)))
        }
        db.transact {
            val e = db.foodEntryDao().get(a)!!
            db.foodEntryDao().upsert(e.copy(enrichment = EnrichmentStatus.NO_PROVIDER, sync = e.sync))
        }
        assertEquals(0, sweep.run())
        ai.assigned[AiTask.ENTRY_PARSE] = local
        assertEquals(1, sweep.run())
        assertEquals(listOf(Triple(a, atA, true)), scheduler.enqueued)
    }
}
