package app.klean.core.ai.ondevice

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Runs [ModelDownloader] in WorkManager, so a 600 MB download survives leaving the screen. */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val downloader: ModelDownloader,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val out = downloader.download(url) { done, total -> setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total)) }
        return when (out) {
            is DownloadResult.Done -> when (out.result) {
                is ModelInstallResult.Installed -> Result.success()
                ModelInstallResult.NoSpace -> Result.failure(workDataOf(KEY_ERROR to ERROR_SPACE))
                else -> Result.failure(workDataOf(KEY_ERROR to ERROR_NOT_A_MODEL))
            }
            DownloadResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(workDataOf(KEY_ERROR to ERROR_NETWORK))
            is DownloadResult.Refused -> Result.failure(workDataOf(KEY_ERROR to ERROR_REFUSED))
            DownloadResult.NoSpace -> Result.failure(workDataOf(KEY_ERROR to ERROR_SPACE))
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val ERROR_NETWORK = "network"
        const val ERROR_REFUSED = "refused"
        const val ERROR_SPACE = "space"
        const val ERROR_NOT_A_MODEL = "not_a_model"
        private const val MAX_ATTEMPTS = 8
    }
}

/** What the settings screen shows about the download. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    /** Queued, e.g. waiting for Wi-Fi. */
    data object Waiting : ModelDownloadState
    data class Running(val done: Long, val total: Long) : ModelDownloadState
    data class Failed(val error: String) : ModelDownloadState
}

interface ModelDownloads {
    val state: Flow<ModelDownloadState>
    fun start(url: String)
    fun cancel()
}

@Singleton
class WorkManagerModelDownloads @Inject constructor(@ApplicationContext private val ctx: Context) : ModelDownloads {
    private val wm get() = WorkManager.getInstance(ctx)

    override val state: Flow<ModelDownloadState> = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(WORK).map { infos ->
        val i = infos.lastOrNull() ?: return@map ModelDownloadState.Idle
        when (i.state) {
            WorkInfo.State.RUNNING -> ModelDownloadState.Running(i.progress.getLong(ModelDownloadWorker.KEY_DONE, 0), i.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1))
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelDownloadState.Waiting
            WorkInfo.State.FAILED -> ModelDownloadState.Failed(i.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: ModelDownloadWorker.ERROR_NETWORK)
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> ModelDownloadState.Idle
        }
    }

    override fun start(url: String) {
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_URL to url))
            // Hundreds of MB: Wi-Fi (or any unmetered network) only.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    override fun cancel() {
        wm.cancelUniqueWork(WORK)
    }

    companion object {
        const val WORK = "ondevice-model-download"
    }
}
