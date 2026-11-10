package app.klean.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** WorkManager scheduling: every 15 minutes while paired, plus an expedited run after local changes. */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val wm get() = WorkManager.getInstance(ctx)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** One run as soon as possible. Appended after a run in progress, so a change made during it is not lost. */
    fun syncNow() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun cancel() {
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(NOW)
    }

    companion object {
        const val PERIODIC = "klean.sync.periodic"
        const val NOW = "klean.sync.now"
        const val BACKOFF_S = 30L
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val runner: SyncRunner,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = when (val o = runner.run()) {
        SyncOutcome.NotPaired -> Result.success()
        is SyncOutcome.Done -> {
            // A page was left because of an unpushed local edit: the next run pushes it first, then settles it.
            if (!o.report.complete && runAttemptCount == 0) scheduler.syncNow()
            Result.success()
        }
        is SyncOutcome.Failed -> if (o.retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    /** Only used for expedited work before Android 12. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, applicationContext.getString(R.string.sync_channel), NotificationManager.IMPORTANCE_MIN),
            )
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.sync_running))
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, n)
    }

    companion object {
        const val CHANNEL = "sync_v1"
        const val NOTIFICATION_ID = 6001
        const val MAX_ATTEMPTS = 8
    }
}
