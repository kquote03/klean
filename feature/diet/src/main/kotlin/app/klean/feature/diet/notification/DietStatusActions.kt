package app.klean.feature.diet.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import app.klean.core.capture.CaptureActivity
import app.klean.feature.diet.R
import app.klean.shared.module.ModuleId
import app.klean.shared.diet.CaptureSource
import app.klean.core.notifications.R as NotifR

/** Action buttons of the diet check-in notifications. The holistic status line has its own (\:core:capture). */
internal object DietStatusActions {
    const val DEEP_LINK_DIET = "klean://diet"
    internal const val RC_CAPTURE = 13

    /** Opens the capture sheet over the current screen, for photos, voice or repeats. */
    fun capture(ctx: Context): NotificationCompat.Action {
        val intent = CaptureActivity.intent(ctx, CaptureSource.NOTIFICATION_REPLY, ModuleId.DIET)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, RC_CAPTURE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(NotifR.drawable.ic_stat_klean, ctx.getString(R.string.diet_status_action_capture), pi).build()
    }

    /**
     * Inline reply. RemoteInput needs a mutable PendingIntent. No authentication and no UI, so it
     * works from the lock screen when the user allows notification content there.
     */
    fun logFood(ctx: Context, source: CaptureSource, requestCode: Int): NotificationCompat.Action {
        val label = ctx.getString(R.string.diet_status_action_log)
        val remote = RemoteInput.Builder(DietReplyReceiver.KEY_TEXT)
            .setLabel(ctx.getString(R.string.diet_status_reply_hint))
            .build()
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode,
            Intent(ctx, DietReplyReceiver::class.java).putExtra(DietReplyReceiver.EXTRA_SOURCE, source.name),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        return NotificationCompat.Action.Builder(NotifR.drawable.ic_stat_klean, label, pi)
            .addRemoteInput(remote)
            .setAllowGeneratedReplies(false)
            .setAuthenticationRequired(false)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }
}
