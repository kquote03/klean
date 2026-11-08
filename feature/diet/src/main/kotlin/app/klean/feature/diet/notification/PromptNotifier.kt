package app.klean.feature.diet.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.R
import app.klean.feature.diet.prompt.PromptPresenter
import app.klean.feature.diet.widget.DietWidgetUpdater
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.time.HOUR_MS
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import app.klean.core.notifications.R as NotifR

/**
 * Shows a check-in. The format picks the channel (plan §5.3): STANDARD → default, HEADS_UP → high,
 * SILENT_VIBRATE → vibrate-only, WIDGET_HIGHLIGHT → no notification, only the widget pill and the
 * status title change. Every notification carries the same inline reply as the status line, and a
 * delete intent so a swipe counts as DISMISSED. Stale check-ins time out after 2 h.
 */
@Singleton
class PromptNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: DevicePrefs,
    private val status: DietStatusNotifier,
    private val widget: DietWidgetUpdater,
) : PromptPresenter {
    override suspend fun present(logId: String, format: PromptFormat, phrasingId: String, trigger: PromptTrigger) {
        if (format == PromptFormat.WIDGET_HIGHLIGHT) {
            prefs.setHighlightPromptId(logId)
            refreshPassiveSurfaces()
            return
        }
        val nm = NotificationManagerCompat.from(ctx)
        if (!canPost(nm)) return
        val phrasing = PromptPhrasings.byId(phrasingId) ?: PromptPhrasings.generic.first()
        val channel = when (format) {
            PromptFormat.HEADS_UP -> Channels.DIET_PROMPT_HEADS_UP
            PromptFormat.SILENT_VIBRATE -> Channels.DIET_PROMPT_VIBRATE
            else -> Channels.DIET_PROMPT
        }
        val builder = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(ctx.getString(phrasing.res))
            .setContentText(ctx.getString(R.string.prompt_body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (format == PromptFormat.HEADS_UP) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            // The wording is generic (never what was eaten), so it can show on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(openDiet())
            .setDeleteIntent(dismissIntent(logId))
            .addAction(DietStatusActions.logFood(ctx, CaptureSource.PROMPT_REPLY, RC_PROMPT_REPLY))
            .addAction(DietStatusActions.capture(ctx))
        post(nm, builder)
    }

    override suspend fun cancelActive() {
        NotificationManagerCompat.from(ctx).cancel(NotificationIds.DIET_PROMPT)
        if (prefs.highlightPromptId.first() != null) {
            prefs.setHighlightPromptId(null)
            refreshPassiveSurfaces()
        }
    }

    private suspend fun refreshPassiveSurfaces() {
        runCatching { widget.update() }
        status.refresh()
    }

    private fun canPost(nm: NotificationManagerCompat): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(nm: NotificationManagerCompat, b: NotificationCompat.Builder) = nm.notify(NotificationIds.DIET_PROMPT, b.build())

    private fun openDiet(): PendingIntent = PendingIntent.getActivity(
        ctx, RC_PROMPT_OPEN,
        Intent(Intent.ACTION_VIEW, DietStatusNotifier.DEEP_LINK_DIET.toUri())
            .setPackage(ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun dismissIntent(logId: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, logId.hashCode(),
        Intent(ctx, PromptDismissReceiver::class.java).putExtra(PromptDismissReceiver.EXTRA_LOG_ID, logId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val TIMEOUT_MS = 2 * HOUR_MS
        /** Distinct from the status line's reply (10): same receiver, so a shared code would overwrite its source extra. */
        internal const val RC_PROMPT_REPLY = 14
        internal const val RC_PROMPT_OPEN = 15
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PromptNotifierModule {
    @Binds abstract fun presenter(impl: PromptNotifier): PromptPresenter
}
