package app.klean.feature.delegation.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.klean.core.common.ApplicationScope
import app.klean.feature.delegation.DelegationNotices
import app.klean.feature.delegation.DelegationRunner
import app.klean.feature.delegation.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object DelegationChannels {
    /** Approvals wait for the user: high importance (plan M23). */
    const val APPROVALS = "approvals"
    const val DELEGATION = "delegation"

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(APPROVALS, ctx.getString(R.string.delegation_channel_approvals), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(DELEGATION, ctx.getString(R.string.delegation_channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }
}

object DelegationNotificationIds {
    /** Tagged with the approval id. */
    const val APPROVAL = 6001
    /** Tagged with the delegation id: a question, then the outcome. */
    const val DELEGATION = 6002
}

/** Notifications for questions (inline reply), approvals (opens the approval screen) and outcomes. */
@Singleton
class AndroidDelegationNotices @Inject constructor(@ApplicationContext private val ctx: Context) : DelegationNotices {
    private fun allowed() = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(uri: String, code: Int) = PendingIntent.getActivity(
        ctx, code, Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(ctx.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun question(delegationId: String, taskTitle: String, text: String) {
        if (!allowed()) return
        DelegationChannels.ensure(ctx)
        val reply = PendingIntent.getBroadcast(
            ctx, delegationId.hashCode(),
            Intent(ctx, DelegationReplyReceiver::class.java).putExtra(DelegationReplyReceiver.EXTRA_ID, delegationId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val input = RemoteInput.Builder(DelegationReplyReceiver.KEY_TEXT).setLabel(ctx.getString(R.string.delegation_reply_hint)).build()
        val action = NotificationCompat.Action.Builder(0, ctx.getString(R.string.delegation_reply), reply).addRemoteInput(input).build()
        val n = NotificationCompat.Builder(ctx, DelegationChannels.DELEGATION)
            .setSmallIcon(R.drawable.ic_delegation)
            .setContentTitle(ctx.getString(R.string.delegation_question_title, taskTitle))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(DelegationLinks.delegation(delegationId), delegationId.hashCode()))
            .addAction(action)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(delegationId, DelegationNotificationIds.DELEGATION, n)
    }

    override fun approval(approvalId: String, taskTitle: String, action: String, recipients: Int) {
        if (!allowed()) return
        DelegationChannels.ensure(ctx)
        val body = if (recipients > 0) ctx.resources.getQuantityString(R.plurals.delegation_approval_body, recipients, recipients)
        else ctx.getString(R.string.delegation_approval_body_none)
        val n = NotificationCompat.Builder(ctx, DelegationChannels.APPROVALS)
            .setSmallIcon(R.drawable.ic_delegation)
            .setContentTitle(ctx.getString(R.string.delegation_approval_title, taskTitle))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open(DelegationLinks.approval(approvalId), approvalId.hashCode()))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(approvalId, DelegationNotificationIds.APPROVAL, n)
    }

    override fun finished(delegationId: String, taskTitle: String, ok: Boolean) {
        if (!allowed()) return
        DelegationChannels.ensure(ctx)
        val n = NotificationCompat.Builder(ctx, DelegationChannels.DELEGATION)
            .setSmallIcon(R.drawable.ic_delegation)
            .setContentTitle(ctx.getString(if (ok) R.string.delegation_done_title else R.string.delegation_failed_title, taskTitle))
            .setContentText(ctx.getString(if (ok) R.string.delegation_done_body else R.string.delegation_failed_body))
            .setContentIntent(open(DelegationLinks.delegation(delegationId), delegationId.hashCode()))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(delegationId, DelegationNotificationIds.DELEGATION, n)
    }

    override fun clear(delegationId: String) {
        NotificationManagerCompat.from(ctx).cancel(delegationId, DelegationNotificationIds.DELEGATION)
    }
}

/** Deep links into the delegation screens. */
object DelegationLinks {
    const val BASE = "klean://delegation"
    const val SETTINGS = "$BASE/settings"
    fun delegation(id: String) = "$BASE/$id"
    fun approval(id: String) = "$BASE/approval/$id"
}

/** Inline reply to an agent's question. */
@AndroidEntryPoint
class DelegationReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var runner: DelegationRunner
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                runCatching { runner.answer(id, text) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "delegation_id"
        const val KEY_TEXT = "reply"
    }
}
