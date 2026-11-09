package app.klean.feature.diet.ai

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiService
import app.klean.core.ai.StructuredRequest
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.CALORIE_ESTIMATE_SCHEMA
import app.klean.shared.ai.CALORIE_ESTIMATE_SYSTEM
import app.klean.shared.ai.ENTRY_PARSE_SCHEMA
import app.klean.shared.ai.ENTRY_PARSE_SYSTEM
import app.klean.shared.ai.ParsedEntryDto
import app.klean.shared.ai.ParsedItemDto
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.MealType
import app.klean.shared.id.UuidV7
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Turns one food entry into AI items (plan §5.5). The raw text is only read, never written. Writes
 * here don't go through the diet change dispatcher: lists observe Room directly, and dispatching would
 * re-queue the entry.
 */
@HiltWorker
class FoodEnrichmentWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val ai: AiService,
    private val images: AiImageLoader,
    private val scheduler: EnrichmentScheduler,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ENTRY) ?: return Result.success()
        val enqueuedAt = inputData.getLong(KEY_UPDATED_AT, 0L)
        val entry = db.foodEntryDao().get(id) ?: return Result.success()
        if (entry.sync.deleted) return Result.success()
        if (entry.sync.updatedAt != enqueuedAt) return reEnqueue(entry)

        val outcome = ai.run(AiTask.ENTRY_PARSE, request(entry), sendImage = true)
        return when (outcome) {
            AiOutcome.NoProvider -> mark(id, EnrichmentStatus.NO_PROVIDER)
            is AiOutcome.Failed -> {
                Log.i(TAG, "Enrichment not possible: ${outcome.reason}")
                mark(id, EnrichmentStatus.FAILED)
            }
            is AiOutcome.Unavailable -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                mark(id, EnrichmentStatus.FAILED)
            } else {
                Result.retry()
            }
            is AiOutcome.Success -> {
                val parsed = parseEntry(outcome.json)?.sanitized() ?: return mark(id, EnrichmentStatus.FAILED)
                val items = withSeparateCalories(parsed, outcome)
                write(entry, parsed.copy(items = items), outcome)
            }
        }
    }

    private suspend fun request(entry: FoodEntryEntity): StructuredRequest {
        val zone = runCatching { TimeZone.of(entry.eatenAtZone) }.getOrDefault(TimeZone.currentSystemDefault())
        val t = Instant.fromEpochMilliseconds(entry.eatenAt).toLocalDateTime(zone)
        val local = "%02d:%02d".format(t.hour, t.minute)
        val image = entry.photoFile?.let { runCatching { images.load(it) }.getOrNull() }
        return StructuredRequest(ENTRY_PARSE_SYSTEM, "Logged at $local local time: ${entry.rawText}", ENTRY_PARSE_SCHEMA, image)
    }

    /** If calorie estimates are assigned to another provider that is available, ask it; keep the first answer otherwise. */
    private suspend fun withSeparateCalories(parsed: ParsedEntryDto, first: AiOutcome.Success): List<ParsedItemDto> {
        val calorieProvider = ai.providerFor(AiTask.CALORIE_ESTIMATE) ?: return parsed.items
        if (calorieProvider.id == first.provider) return parsed.items
        val list = parsed.items.joinToString(", ") { i -> i.portion?.let { "${i.name} ($it)" } ?: i.name }
        val out = ai.run(
            AiTask.CALORIE_ESTIMATE,
            StructuredRequest(CALORIE_ESTIMATE_SYSTEM, "Estimate calories for: $list", CALORIE_ESTIMATE_SCHEMA),
            sendImage = false,
        ) as? AiOutcome.Success ?: return parsed.items
        val calories = runCatching {
            out.json.jsonObject["items"]!!.jsonArray.map { calories(it.jsonObject["calories"]) }
        }.getOrNull() ?: return parsed.items
        return parsed.items.mapIndexed { i, item ->
            val c = calories.getOrNull(i)?.takeIf { it in 0..5000 }
            if (c != null) item.copy(calories = c) else item
        }
    }

    private suspend fun write(entry: FoodEntryEntity, parsed: ParsedEntryDto, outcome: AiOutcome.Success): Result {
        val stale = db.transact {
            val now = db.foodEntryDao().get(entry.id) ?: return@transact null
            if (now.sync.deleted) return@transact null
            if (now.sync.updatedAt != entry.sync.updatedAt) return@transact now
            val existing = db.foodItemDao().forEntry(entry.id).filterNot { it.sync.deleted }
            val replaced = existing.filter { it.state == FoodItemState.ACCEPTED && it.providerId != USER }
            val kept = existing - replaced.toSet()
            val firstPosition = (kept.maxOfOrNull { it.position } ?: -1) + 1
            val tombstones = replaced.map { it.copy(sync = stamper.stamp(it.sync, deleted = true)) }
            val fresh = parsed.items.mapIndexed { i, item ->
                val sync = stamper.stamp()
                FoodItemEntity(
                    id = UuidV7.generate(sync.createdAt), entryId = entry.id, position = firstPosition + i,
                    name = item.name, portion = item.portion, calories = item.calories,
                    providerId = outcome.provider.wire, model = outcome.model, state = FoodItemState.ACCEPTED, sync = sync,
                )
            }
            db.foodItemDao().upsertAll(tombstones + fresh)
            val meal = parsed.mealType?.let { m -> MealType.entries.firstOrNull { it.name.equals(m, ignoreCase = true) } }
            db.foodEntryDao().upsert(
                now.copy(
                    mealType = meal ?: now.mealType,
                    mealTypeProvider = if (meal != null) outcome.provider.wire else now.mealTypeProvider,
                    enrichment = EnrichmentStatus.DONE,
                    sync = stamper.stamp(now.sync),
                ),
            )
            null
        }
        return if (stale != null) reEnqueue(stale) else Result.success()
    }

    private suspend fun mark(id: String, status: EnrichmentStatus): Result {
        db.transact {
            val e = db.foodEntryDao().get(id) ?: return@transact
            if (e.sync.deleted || e.enrichment == status) return@transact
            db.foodEntryDao().upsert(e.copy(enrichment = status, sync = stamper.stamp(e.sync)))
        }
        return Result.success()
    }

    /** The entry changed since this work was queued: queue it again for the current version, write nothing. */
    private suspend fun reEnqueue(entry: FoodEntryEntity): Result {
        val p = ai.providerFor(AiTask.ENTRY_PARSE) ?: return mark(entry.id, EnrichmentStatus.NO_PROVIDER)
        scheduler.enqueue(entry.id, entry.sync.updatedAt, p.needsNetwork, replace = false)
        return Result.success()
    }

    companion object {
        const val TAG = "KleanAi"
        const val KEY_ENTRY = "entryId"
        const val KEY_UPDATED_AT = "enqueuedUpdatedAt"
        const val MAX_ATTEMPTS = 8
        private const val USER = "user"

        fun input(entryId: String, enqueuedUpdatedAt: Long): Data =
            workDataOf(KEY_ENTRY to entryId, KEY_UPDATED_AT to enqueuedUpdatedAt)
    }
}

internal fun parseEntry(json: JsonElement): ParsedEntryDto? = ParsedEntryDto.fromJson(json)

private fun calories(el: JsonElement?): Int? = ParsedEntryDto.caloriesOf(el)
