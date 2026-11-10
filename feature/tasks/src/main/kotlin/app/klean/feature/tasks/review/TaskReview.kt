package app.klean.feature.tasks.review

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.QuietHoursService
import app.klean.core.notifications.Reschedulable
import app.klean.feature.tasks.R
import app.klean.feature.tasks.data.TasksKeys
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** Pure rules for the once-a-day "Still want these?" notification. */
object ReviewPolicy {
    /** Something from an earlier day is still open, and the review hasn't shown today. */
    fun reviewNeeded(pastDue: Int, lastShown: LocalDate?, today: LocalDate): Boolean = pastDue > 0 && lastShown != today

    /** Today at [minute] if that's still ahead, otherwise tomorrow, then moved out of quiet hours. */
    inline fun nextAt(now: Long, minute: Int, zone: TimeZone, nextAllowed: (Long) -> Long): Long {
        val today = now.toLocal(zone).date
        val todayAt = atMinute(today, minute, zone)
        val at = if (todayAt > now) todayAt else atMinute(today.plus(DatePeriod(days = 1)), minute, zone)
        return nextAllowed(at)
    }
}

/**
 * The daily review: an inexact alarm at 10:00 (Tasks settings), out of quiet hours, re-armed after boot.
 * When it fires on the main phone with something from earlier days still open, one low-importance
 * notification: "Still want these?" / "3 tasks from earlier days". Tapping it opens Tasks' Today tab,
 * where the review sits at the top.
 */
@Singleton
class TaskReview @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: TasksRepository,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val prefs: DevicePrefs,
    private val quiet: QuietHoursService,
    private val alarms: AlarmScheduler,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : Reschedulable {
    private fun now() = clock.now().toEpochMilliseconds()

    override suspend fun reschedule() {
        ensureChannel(ctx)
        if (!gate.isEnabled(ModuleId.TASKS) || !settings.get(TasksKeys.ReviewEnabled)) {
            cancel()
            return
        }
        val at = ReviewPolicy.nextAt(now(), settings.get(TasksKeys.ReviewMinute), zone.current()) { quiet.nextAllowed(it) }
        alarms.scheduleInexact(at, pending(ctx, create = true)!!)
    }

    fun cancel() {
        pending(ctx, create = false)?.let(alarms::cancel)
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
    }

    /** The alarm went off (or [force]: the debug broadcast). Returns true when the notification was posted. */
    suspend fun onAlarm(force: Boolean = false): Boolean {
        try {
            if (!gate.isEnabled(ModuleId.TASKS)) return false
            if (!force && (!settings.get(TasksKeys.ReviewEnabled) || !prefs.isMainPhone.first())) return false
            val today = repo.today()
            val pastDue = repo.pastDue().filter { it.parentId == null }
            val last = settings.get(TasksKeys.ReviewShownOn).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (pastDue.isEmpty() || (!force && !ReviewPolicy.reviewNeeded(pastDue.size, last, today))) return false
            if (!post(pastDue.size)) return false
            settings.set(TasksKeys.ReviewShownOn, today.toString())
            return true
        } finally {
            if (!force) reschedule()
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(count: Int): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return false
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(Intent.ACTION_VIEW, DEEP_LINK_REVIEW.toUri()).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_task)
            .setContentTitle(ctx.getString(R.string.tasks_review_title))
            .setContentText(ctx.resources.getQuantityString(R.plurals.tasks_review_body, count, count))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        return true
    }

    companion object {
        const val CHANNEL = "task_review"
        const val NOTIFICATION_ID = 2001
        const val REQUEST_CODE = 2001
        const val DEEP_LINK_REVIEW = "klean://tasks/review"

        fun ensureChannel(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.tasks_channel_review), NotificationManager.IMPORTANCE_LOW),
            )
        }

        fun pending(ctx: Context, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, Intent(ctx, TaskReviewReceiver::class.java).setAction(ACTION_REVIEW),
            PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )

        const val ACTION_REVIEW = "app.klean.tasks.REVIEW"
    }
}

/**
 * The review alarm. Debug builds also accept `<applicationId>.TASK_REVIEW` (declared in the debug
 * manifest), which shows the notification at once if anything from earlier days is open.
 */
@AndroidEntryPoint
class TaskReviewReceiver : BroadcastReceiver() {
    @Inject lateinit var review: TaskReview
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val debug = intent.action == context.packageName + ".TASK_REVIEW"
        if (intent.action != TaskReview.ACTION_REVIEW && !debug) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { review.onAlarm(force = debug) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "task review failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Once a day, done tasks older than 30 days leave the Done view (archived, still synced). No manual cleanup. */
@HiltWorker
class TaskArchiveWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: TasksRepository,
    private val gate: ModuleGate,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (gate.isEnabled(ModuleId.TASKS)) repo.archiveOldCompleted()
        return Result.success()
    }

    companion object {
        const val NAME = "tasks-archive"

        fun enqueue(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TaskArchiveWorker>(1, TimeUnit.DAYS).build(),
        )

        fun cancel(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}

/** Keeps the archive worker queued (after install, update, boot): it's a Reschedulable like the alarms. */
class TaskArchiveScheduling @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val gate: ModuleGate,
) : Reschedulable {
    override suspend fun reschedule() {
        if (gate.isEnabled(ModuleId.TASKS)) TaskArchiveWorker.enqueue(ctx) else TaskArchiveWorker.cancel(ctx)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskReviewModule {
    @Binds @IntoSet abstract fun review(r: TaskReview): Reschedulable
    @Binds @IntoSet abstract fun archive(r: TaskArchiveScheduling): Reschedulable
}
