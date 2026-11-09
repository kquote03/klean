package app.klean.feature.diet.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.klean.core.ai.AiConfigListener
import app.klean.core.ai.AiService
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.photo.PhotoStorage
import app.klean.core.notifications.Reschedulable
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.EnrichmentStatus
import app.klean.shared.time.HOUR_MS
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads an entry's photo for AI, downscaled to a 1024 px long edge. Null if there is none. */
fun interface AiImageLoader {
    suspend fun load(photoFile: String): ByteArray?
}

class AndroidAiImageLoader @Inject constructor(private val photos: PhotoStorage) : AiImageLoader {
    override suspend fun load(photoFile: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = photos.file(photoFile)
        if (!f.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return@withContext null
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        val scale = MAX_EDGE.toFloat() / max(bmp.width, bmp.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
        } else {
            bmp
        }
        ByteArrayOutputStream().use { s -> out.compress(Bitmap.CompressFormat.JPEG, 85, s); s.toByteArray() }
    }

    private companion object {
        const val MAX_EDGE = 1024
    }
}

/** Queues [FoodEnrichmentWorker] for one entry. */
interface EnrichmentScheduler {
    /** [replace] = a newer version of the entry supersedes queued work; the sweep keeps what's queued. */
    fun enqueue(entryId: String, enqueuedUpdatedAt: Long, needsNetwork: Boolean, replace: Boolean = true)
}

class WorkManagerEnrichmentScheduler @Inject constructor(@ApplicationContext private val ctx: Context) : EnrichmentScheduler {
    override fun enqueue(entryId: String, enqueuedUpdatedAt: Long, needsNetwork: Boolean, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<FoodEnrichmentWorker>()
            .setInputData(FoodEnrichmentWorker.input(entryId, enqueuedUpdatedAt))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .addTag(TAG)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "ai-enrich-$entryId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val TAG = "ai-enrich"
    }
}

/**
 * After a log or a text edit, queue enrichment if a provider is assigned and enabled. Only entries the
 * repository marked PENDING are queued, so time or photo edits and undo don't re-run AI.
 */
class EnrichmentEnqueuer @Inject constructor(
    private val db: KleanDatabase,
    private val ai: AiService,
    private val scheduler: EnrichmentScheduler,
) : DietChangeListener {
    override suspend fun onChange(change: DietChange) {
        val id = when (change) {
            is DietChange.Logged -> change.id
            is DietChange.Edited -> change.id
            else -> return
        }
        val provider = ai.providerFor(AiTask.ENTRY_PARSE) ?: return
        val e = db.foodEntryDao().get(id) ?: return
        if (e.sync.deleted || e.enrichment != EnrichmentStatus.PENDING) return
        scheduler.enqueue(id, e.sync.updatedAt, provider.needsNetwork)
    }
}

/** Queues entries from the last 14 days that have no AI details yet. Runs on provider changes and every 12 h. */
class EnrichmentSweep @Inject constructor(
    private val db: KleanDatabase,
    private val ai: AiService,
    private val scheduler: EnrichmentScheduler,
    private val clock: Clock,
) {
    suspend fun run(): Int {
        val provider = ai.providerFor(AiTask.ENTRY_PARSE) ?: return 0
        val since = clock.now().toEpochMilliseconds() - LOOKBACK_MS
        val ids = db.foodEntryDao().awaitingEnrichment(since)
        ids.forEach { id ->
            val e = db.foodEntryDao().get(id) ?: return@forEach
            scheduler.enqueue(id, e.sync.updatedAt, provider.needsNetwork, replace = false)
        }
        return ids.size
    }

    private companion object {
        const val LOOKBACK_MS = 14 * 24 * HOUR_MS
    }
}

@HiltWorker
class EnrichmentSweepWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val sweep: EnrichmentSweep,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        sweep.run()
        return Result.success()
    }
}

/** Runs the sweep now when provider settings change, and keeps the 12 h periodic sweep scheduled. */
class EnrichmentSweepScheduler @Inject constructor(@ApplicationContext private val ctx: Context) : AiConfigListener, Reschedulable {
    override suspend fun onAiConfigChanged() {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EnrichmentSweepWorker>().build(),
        )
    }

    override suspend fun reschedule() {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<EnrichmentSweepWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    private companion object {
        const val NOW = "ai-sweep-now"
        const val PERIODIC = "ai-sweep-periodic"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EnrichmentModule {
    @Binds abstract fun images(impl: AndroidAiImageLoader): AiImageLoader
    @Binds abstract fun scheduler(impl: WorkManagerEnrichmentScheduler): EnrichmentScheduler
    @Binds @IntoSet abstract fun enqueuer(impl: EnrichmentEnqueuer): DietChangeListener
    @Binds @IntoSet abstract fun configListener(impl: EnrichmentSweepScheduler): AiConfigListener
    @Binds @IntoSet abstract fun periodicSweep(impl: EnrichmentSweepScheduler): Reschedulable
}
