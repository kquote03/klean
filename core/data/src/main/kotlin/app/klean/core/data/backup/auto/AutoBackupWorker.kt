package app.klean.core.data.backup.auto

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.klean.core.common.ZoneProvider
import app.klean.core.data.backup.BackupManager
import app.klean.core.data.prefs.DevicePrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Daily backup into the folder the user picked. Writes `klean-backup-<yyyyMMdd-HHmm>.sqlite`, then keeps
 * only the newest `autoBackupKeep` of those files. Other files in the folder are never touched.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupManager,
    private val prefs: DevicePrefs,
    private val targets: BackupTargetFactory,
    private val notifier: BackupFailureNotifier,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!prefs.autoBackupEnabled.first()) return Result.success()
        return try {
            runBackup()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Auto backup attempt ${runAttemptCount + 1} did not complete", e)
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                notifier.backupFailed()
                Result.failure()
            }
        }
    }

    private suspend fun runBackup() {
        val uri = prefs.autoBackupTreeUri.first() ?: throw IOException("No backup folder chosen")
        val target = targets.open(uri) ?: throw IOException("Backup folder not reachable")
        val name = fileName(clock.now(), zone.current())
        if (name in target.list()) target.delete(name)
        try {
            target.create(name).use { backup.exportTo(it) }
        } catch (e: Exception) {
            runCatching { target.delete(name) }
            throw e
        }
        toDelete(target.list(), prefs.autoBackupKeep.first().coerceAtLeast(1)).forEach { target.delete(it) }
    }

    companion object {
        private const val TAG = "AutoBackup"
        const val MAX_RETRIES = 3
        const val PREFIX = "klean-backup-"
        const val SUFFIX = ".sqlite"

        fun fileName(at: Instant, zone: TimeZone): String {
            val t = at.toLocalDateTime(zone)
            fun Int.two() = toString().padStart(2, '0')
            return "$PREFIX${t.year}${t.month.ordinal.plus(1).two()}${t.day.two()}-${t.hour.two()}${t.minute.two()}$SUFFIX"
        }

        /** Our backup files beyond the newest [keep]. Names sort by time because the stamp is fixed-width. */
        fun toDelete(names: List<String>, keep: Int): List<String> =
            names.filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }.sortedDescending().drop(keep)
    }
}
