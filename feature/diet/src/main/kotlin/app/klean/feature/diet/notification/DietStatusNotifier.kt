package app.klean.feature.diet.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietChangeListener
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.data.TodaySummary
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.klean.core.notifications.R as NotifR

/**
 * The persistent "What did you eat?" status line (plan §5.2). A regular ongoing notification, not a
 * foreground service: nothing runs continuously. Re-posted on every diet change, on swipe-away
 * (StatusRepostReceiver) and at app start. Respects the module switch, the setting, the channel
 * toggle and the notification permission.
 *
 * Takes the repository lazily: the repository's dispatcher holds this listener, so a direct
 * dependency would be a cycle.
 */
@Singleton
class DietStatusNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<DietRepository>,
    private val gate: ModuleGate,
    private val settings: SettingsRepository,
    private val fmt: DietFormatter,
) : DietChangeListener {
    private val mutex = Mutex()

    override suspend fun onChange(change: DietChange) = refresh()

    /** Posts or updates the status. [savedText] shows "Saved: …" once, e.g. right after an inline reply. */
    suspend fun refresh(savedText: String? = null) = mutex.withLock {
        val nm = NotificationManagerCompat.from(ctx)
        if (!gate.isEnabled(ModuleId.DIET) || !settings.get(SyncedKeys.DietStatusNotification)) {
            nm.cancel(NotificationIds.DIET_STATUS)
            return@withLock
        }
        if (!canPost(nm)) return@withLock
        post(nm, build(repo.get().todaySummary(), savedText))
    }

    fun cancel() = NotificationManagerCompat.from(ctx).cancel(NotificationIds.DIET_STATUS)

    private fun canPost(nm: NotificationManagerCompat): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(nm: NotificationManagerCompat, n: Notification) = nm.notify(NotificationIds.DIET_STATUS, n)

    internal fun build(summary: TodaySummary, savedText: String?): Notification {
        val res = ctx.resources
        val text = when {
            savedText != null -> ctx.getString(R.string.diet_status_saved, savedText)
            summary.count == 0 || summary.lastText == null || summary.lastAt == null -> ctx.getString(R.string.diet_status_empty)
            else -> res.getQuantityString(R.plurals.diet_status_summary, summary.count, summary.count, summary.lastText, fmt.time(summary.lastAt))
        }
        val public = NotificationCompat.Builder(ctx, Channels.DIET_STATUS)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(res.getQuantityString(R.plurals.diet_status_public, summary.count, summary.count))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        val builder = NotificationCompat.Builder(ctx, Channels.DIET_STATUS)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(ctx.getString(R.string.diet_status_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openDietIntent())
            .setDeleteIntent(repostIntent())
        DietStatusActions.addTo(ctx, builder)
        return builder.build()
    }

    private fun openDietIntent(): PendingIntent = PendingIntent.getActivity(
        ctx, RC_OPEN,
        Intent(Intent.ACTION_VIEW, DEEP_LINK_DIET.toUri())
            .setPackage(ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun repostIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, RC_REPOST, Intent(ctx, StatusRepostReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val DEEP_LINK_DIET = "klean://diet"
        internal const val RC_REPLY = 10
        internal const val RC_REPOST = 11
        internal const val RC_OPEN = 12
        internal const val RC_CAPTURE = 13
    }
}
