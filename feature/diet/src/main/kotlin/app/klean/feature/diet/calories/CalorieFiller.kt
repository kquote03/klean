package app.klean.feature.diet.calories

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiService
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.CALORIE_ESTIMATE_SCHEMA
import app.klean.shared.ai.CALORIE_ESTIMATE_SYSTEM
import app.klean.shared.ai.ParsedEntryDto
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.KcalSource
import app.klean.shared.time.HOUR_MS
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Queues [CalorieFillWorker] for one entry. */
fun interface CalorieFillScheduler {
    fun enqueue(entryId: String, needsNetwork: Boolean)
}

/**
 * Fills an entry's missing calories: the AI items' own calories if the entry has them, then earlier
 * entries of the same food (on-device), then — only if a provider is assigned to Calorie estimates —
 * a background AI estimate. Otherwise the entry simply stays without calories.
 */
class CalorieFiller @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val ai: AiService,
    private val clock: Clock,
    private val scheduler: CalorieFillScheduler,
    private val dispatcher: DietChangeDispatcher,
) : CalorieFill {

    override suspend fun fill(entryId: String): FillResult {
        val e = db.foodEntryDao().get(entryId)?.takeIf { !it.sync.deleted && it.kcal == null } ?: return FillResult.NotPossible

        val itemsKcal = db.foodItemDao().forEntry(entryId)
            .filter { !it.sync.deleted && it.state != FoodItemState.REJECTED }
            .sumOf { it.calories ?: 0 }
        if (itemsKcal > 0 && write(db, stamper, dispatcher, e, itemsKcal, KcalSource.AI)) return FillResult.Filled(KcalSource.AI)

        val since = clock.now().toEpochMilliseconds() - HISTORY_DAYS * 24 * HOUR_MS
        val past = db.foodEntryDao().withCaloriesSince(since).filter { it.id != entryId }.mapNotNull { p ->
            val source = KcalSource.entries.firstOrNull { it.name == p.kcalSource } ?: return@mapNotNull null
            PastEntry(p.rawText, p.kcal ?: return@mapNotNull null, p.grams, source, p.eatenAt)
        }
        historyKcal(e.rawText, e.grams, past)?.let { k ->
            if (write(db, stamper, dispatcher, e, k, KcalSource.HISTORY)) return FillResult.Filled(KcalSource.HISTORY)
        }

        val provider = ai.providerFor(AiTask.CALORIE_ESTIMATE) ?: return FillResult.NotPossible
        // The same provider is already parsing this entry into items with calories; that fills it.
        if (e.enrichment == EnrichmentStatus.PENDING && ai.providerFor(AiTask.ENTRY_PARSE)?.id == provider.id) {
            return FillResult.NotPossible
        }
        scheduler.enqueue(entryId, provider.needsNetwork)
        return FillResult.QueuedForAi
    }

    companion object {
        const val HISTORY_DAYS = 365L

        /**
         * Writes [kcal] only while the entry still has none and still says what [asked] said (text and grams),
         * so a user edit in between always wins. Returns whether it wrote.
         */
        internal suspend fun write(
            db: KleanDatabase, stamper: SyncStamper, dispatcher: DietChangeDispatcher,
            asked: FoodEntryEntity, kcal: Int, source: KcalSource,
        ): Boolean {
            val wrote = db.transact {
                val now = db.foodEntryDao().get(asked.id) ?: return@transact false
                if (now.sync.deleted || now.kcal != null || now.rawText != asked.rawText || now.grams != asked.grams) return@transact false
                db.foodEntryDao().upsert(now.copy(kcal = kcal, kcalSource = source.name, sync = stamper.stamp(now.sync)))
                true
            }
            // Refresh, not Edited: an edit would queue enrichment again.
            if (wrote) dispatcher.dispatch(DietChange.Refresh)
            return wrote
        }
    }
}

/** Asks the provider assigned to Calorie estimates for one entry. Sends the text and grams, never a photo. */
@HiltWorker
class CalorieFillWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val ai: AiService,
    private val dispatcher: DietChangeDispatcher,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ENTRY) ?: return Result.success()
        val e = db.foodEntryDao().get(id)?.takeIf { !it.sync.deleted && it.kcal == null } ?: return Result.success()
        val ask = "Estimate calories for: ${e.rawText}${e.grams?.let { " ($it g)" } ?: ""}"
        return when (val out = ai.run(AiTask.CALORIE_ESTIMATE, StructuredRequest(CALORIE_ESTIMATE_SYSTEM, ask, CALORIE_ESTIMATE_SCHEMA), sendImage = false)) {
            is AiOutcome.Success -> {
                val total = runCatching {
                    out.json.jsonObject["items"]!!.jsonArray.sumOf { ParsedEntryDto.caloriesOf(it.jsonObject["calories"]) ?: 0 }
                }.getOrNull()
                if (total != null && total in 1..MAX_KCAL) {
                    CalorieFiller.write(db, stamper, dispatcher, e, total, KcalSource.AI)
                } else {
                    Log.i(TAG, "Calorie estimate not usable")
                }
                Result.success()
            }
            is AiOutcome.Unavailable -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.success() else Result.retry()
            is AiOutcome.Failed, AiOutcome.NoProvider -> Result.success()
        }
    }

    companion object {
        const val TAG = "KleanAi"
        const val KEY_ENTRY = "entryId"
        const val MAX_ATTEMPTS = 5
        const val MAX_KCAL = 5000

        fun input(entryId: String): Data = workDataOf(KEY_ENTRY to entryId)
    }
}

class WorkManagerCalorieFillScheduler @Inject constructor(@ApplicationContext private val ctx: Context) : CalorieFillScheduler {
    override fun enqueue(entryId: String, needsNetwork: Boolean) {
        // Not expedited: expedited work needs a foreground notification below Android 12. With no
        // constraint met already, a plain one-time request starts right away anyway.
        val request = OneTimeWorkRequestBuilder<CalorieFillWorker>()
            .setInputData(CalorieFillWorker.input(entryId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("calories-$entryId", ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val TAG = "calories"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalorieFillModule {
    @Binds abstract fun fill(impl: CalorieFiller): CalorieFill
    @Binds abstract fun scheduler(impl: WorkManagerCalorieFillScheduler): CalorieFillScheduler
}
