package app.klean.core.capture.status

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
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.klean.core.capture.R
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusSurface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.klean.core.notifications.R as NotifR

/**
 * The persistent "Today" status line (holistic plan H3). One ongoing, silent notification whose text
 * joins the enabled modules' [app.klean.core.ui.module.StatusLineProvider] fragments, with a single
 * inline reply, "Add something", that goes through universal capture with no UI ([StatusReplyReceiver]).
 * Not a foreground service: re-posted on writes ([app.klean.core.ui.module.StatusRefresh]), when the
 * line changes, on swipe-away ([StatusRepostReceiver]) and at app start.
 *
 * Shown while any enabled module contributes a line; respects the status setting, the channel toggle
 * and the notification permission.
 */
@Singleton
class KleanStatusNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val statusLine: StatusLine,
    private val settings: SettingsRepository,
) : StatusSurface {
    private val mutex = Mutex()

    /** "Saved to Diet", shown until the line changes after that save. */
    private var confirmation: Pair<String, List<StatusFragment>>? = null

    override suspend fun refresh() = refresh(savedTo = null)

    /** Posts or updates the status. [savedTo] is a module label: "Saved to <label>" after an inline reply. */
    suspend fun refresh(savedTo: String?) = mutex.withLock {
        val nm = NotificationManagerCompat.from(ctx)
        if (!settings.get(SyncedKeys.DietStatusNotification) || !statusLine.hasProviders().first()) {
            confirmation = null
            nm.cancel(NotificationIds.STATUS)
            return@withLock
        }
        if (!canPost(nm)) return@withLock
        val line = statusLine.fragments().first()
        confirmation = when {
            savedTo != null -> savedTo to line
            confirmation?.second == line -> confirmation
            else -> null
        }
        post(nm, build(line, confirmation?.first))
    }

    /** Re-posts whenever the line itself changes (debounced), e.g. a module's data or the gate. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            // Never let a failing source end the app scope (or a Robolectric test without SQLite).
            try {
                statusLine.fragments().distinctUntilChanged().drop(1).debounce(DEBOUNCE_MS).collect {
                    runCatching { refresh() }.onFailure { Log.w("Klean", "status refresh failed", it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "status line stopped", e)
            }
        }
    }

    fun cancel() = NotificationManagerCompat.from(ctx).cancel(NotificationIds.STATUS)

    private fun canPost(nm: NotificationManagerCompat): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(nm: NotificationManagerCompat, n: Notification) = nm.notify(NotificationIds.STATUS, n)

    internal fun build(line: List<StatusFragment>, savedTo: String?): Notification {
        val text = if (line.isEmpty()) ctx.getString(R.string.status_empty) else line.joinToString(" · ") { it.text }
        // The lock-screen version says how much there is, never what.
        val publicTitle = if (line.isEmpty()) {
            ctx.getString(R.string.status_title)
        } else {
            ctx.resources.getQuantityString(R.plurals.status_public, line.size, line.size)
        }
        val public = NotificationCompat.Builder(ctx, Channels.STATUS)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(publicTitle)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return NotificationCompat.Builder(ctx, Channels.STATUS)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(ctx.getString(R.string.status_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(savedTo?.let { ctx.getString(R.string.capture_saved_to, it) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openToday())
            .setDeleteIntent(repostIntent())
            .addAction(addSomething())
            .build()
    }

    /**
     * Inline reply. RemoteInput needs a mutable PendingIntent. No authentication and no UI, so it
     * works from the lock screen when the user allows notification content there.
     */
    private fun addSomething(): NotificationCompat.Action {
        val remote = RemoteInput.Builder(StatusReplyReceiver.KEY_TEXT)
            .setLabel(ctx.getString(R.string.status_reply_hint))
            .build()
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            ctx, RC_REPLY, Intent(ctx, StatusReplyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        return NotificationCompat.Action.Builder(NotifR.drawable.ic_stat_klean, ctx.getString(R.string.status_action_add), pi)
            .addRemoteInput(remote)
            .setAllowGeneratedReplies(false)
            .setAuthenticationRequired(false)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun openToday(): PendingIntent = PendingIntent.getActivity(
        ctx, RC_OPEN,
        Intent(Intent.ACTION_VIEW, DEEP_LINK_TODAY.toUri())
            .setPackage(ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun repostIntent(): PendingIntent = PendingIntent.getBroadcast(
        ctx, RC_REPOST, Intent(ctx, StatusRepostReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val DEEP_LINK_TODAY = "klean://today"
        const val DEBOUNCE_MS = 500L
        // 10–17 were the diet status and prompt codes; distinct codes keep each PendingIntent's extras apart.
        internal const val RC_REPLY = 20
        internal const val RC_REPOST = 21
        internal const val RC_OPEN = 22
    }
}
