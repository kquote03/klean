package app.klean.core.data.backup.auto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.klean.core.data.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** A folder that holds backup files. The real one is a SAF tree; tests use a temp directory. */
interface BackupTarget {
    /** Names of the files directly in the folder. */
    fun list(): List<String>
    fun create(name: String): OutputStream
    fun delete(name: String): Boolean
}

/** Opens the folder the user picked, or null if it is gone or no longer writable. */
fun interface BackupTargetFactory {
    fun open(treeUri: String): BackupTarget?
}

/** Tells the user, quietly, that automatic backups have stopped working. */
fun interface BackupFailureNotifier {
    fun backupFailed()
}

/** Turns the daily backup on or off. */
fun interface AutoBackupScheduler {
    fun apply(enabled: Boolean)
}

class SafBackupTargetFactory @Inject constructor(@ApplicationContext private val ctx: Context) : BackupTargetFactory {
    override fun open(treeUri: String): BackupTarget? {
        val tree = DocumentFile.fromTreeUri(ctx, Uri.parse(treeUri))?.takeIf { it.isDirectory && it.canWrite() } ?: return null
        return object : BackupTarget {
            override fun list() = tree.listFiles().mapNotNull { it.name }
            override fun create(name: String): OutputStream {
                // octet-stream keeps the display name as given; providers may append an extension for other types.
                val doc = tree.createFile("application/octet-stream", name) ?: throw IOException("Cannot create $name")
                return ctx.contentResolver.openOutputStream(doc.uri, "w") ?: throw IOException("Cannot open $name")
            }
            override fun delete(name: String) = tree.findFile(name)?.delete() ?: false
        }
    }
}

class WorkManagerAutoBackupScheduler @Inject constructor(@ApplicationContext private val ctx: Context) : AutoBackupScheduler {
    override fun apply(enabled: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "auto-backup"
    }
}

/**
 * Posts on its own low-importance `backup` channel (no sound, no peek). The channel is created here
 * because `:core:data` can't depend on `:core:notifications`.
 */
class AndroidBackupFailureNotifier @Inject constructor(@ApplicationContext private val ctx: Context) : BackupFailureNotifier {
    override fun backupFailed() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.backup_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = ctx.getString(R.string.backup_channel_description) },
        )
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = ctx.getString(R.string.backup_auto_failed)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(ctx.getString(R.string.backup_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing else to do.
        }
    }

    companion object {
        const val CHANNEL_ID = "backup"
        const val NOTIFICATION_ID = 9_001
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AutoBackupModule {
    @Binds abstract fun targets(impl: SafBackupTargetFactory): BackupTargetFactory
    @Binds abstract fun notifier(impl: AndroidBackupFailureNotifier): BackupFailureNotifier
    @Binds abstract fun scheduler(impl: WorkManagerAutoBackupScheduler): AutoBackupScheduler
}
