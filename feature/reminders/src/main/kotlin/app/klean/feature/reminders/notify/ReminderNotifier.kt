package app.klean.feature.reminders.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.feature.reminders.R
import app.klean.feature.reminders.ReminderFormatter
import app.klean.feature.reminders.alarm.AlarmActivity
import app.klean.feature.reminders.alarm.SnoozeActivity
import app.klean.shared.diet.prompt.PromptFormat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Posts and clears reminder notifications. One per reminder: tag = reminder id. */
interface ReminderNotifier {
    /** A ping in [format]. [quiet]: on the quiet channel whatever the format (an alarm on another phone, or after auto-stop). */
    fun ping(r: ReminderEntity, format: PromptFormat, quiet: Boolean = false)
    /** A looping alarm notification, used when the ringing service can't start. */
    fun alarmFallback(r: ReminderEntity)
    fun cancel(id: String)
}

class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val fmt: ReminderFormatter,
) : ReminderNotifier {
    private val nm get() = NotificationManagerCompat.from(ctx)

    @SuppressLint("MissingPermission")
    override fun ping(r: ReminderEntity, format: PromptFormat, quiet: Boolean) {
        if (!nm.areNotificationsEnabled()) return
        ReminderChannels.ensure(ctx)
        val channel = if (quiet) ReminderChannels.PING_QUIET else ReminderChannels.forFormat(format)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(r.title)
            .setContentText(ctx.getString(R.string.reminders_ping_text, fmt.time(r.fireAt)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (format == PromptFormat.HEADS_UP && !quiet) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openReminders(ctx))
            .setDeleteIntent(ReminderIntents.action(ctx, r.id, ReminderIntents.SWIPED))
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.reminders_action_done), ReminderIntents.action(ctx, r.id, ReminderIntents.DONE))
            .addAction(0, ctx.getString(R.string.reminders_action_snooze_10), ReminderIntents.action(ctx, r.id, ReminderIntents.SNOOZE_10))
            .addAction(0, ctx.getString(R.string.reminders_action_later), SnoozeActivity.pendingIntent(ctx, r.id))
            .extend(watchActions(ctx, r.id, ReminderIntents.DONE, R.string.reminders_action_done))
            .build()
        nm.notify(r.id, NOTIFICATION_ID, n)
    }

    @SuppressLint("MissingPermission")
    override fun alarmFallback(r: ReminderEntity) {
        if (!nm.areNotificationsEnabled()) return
        ReminderChannels.ensure(ctx)
        val n = alarmNotification(ctx, fmt, r, ReminderChannels.ALARM_SOUND)
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(r.id, NOTIFICATION_ID, n)
    }

    override fun cancel(id: String) = nm.cancel(id, NOTIFICATION_ID)

    companion object {
        const val NOTIFICATION_ID = 3001

        /** The ringing alarm: full-screen [AlarmActivity] over the lock screen, or a heads-up while the phone is in use. */
        fun alarmNotification(ctx: Context, fmt: ReminderFormatter, r: ReminderEntity, channel: String = ReminderChannels.ALARM): Notification {
            val full = AlarmActivity.pendingIntent(ctx, r.id)
            return NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(r.title)
                .setContentText(ctx.getString(R.string.reminders_alarm_text, fmt.time(r.fireAt)))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(0, ctx.getString(R.string.reminders_action_dismiss), ReminderIntents.action(ctx, r.id, ReminderIntents.DISMISS))
                .addAction(0, ctx.getString(R.string.reminders_action_snooze_10), ReminderIntents.action(ctx, r.id, ReminderIntents.SNOOZE_10))
                .addAction(0, ctx.getString(R.string.reminders_action_later), SnoozeActivity.pendingIntent(ctx, r.id))
                .extend(watchActions(ctx, r.id, ReminderIntents.DISMISS, R.string.reminders_action_dismiss))
                .build()
        }

        /**
         * The watch's buttons (M27): the phone's first action and "10 min", both handled on the phone with
         * no UI. "Later…" opens a picker on the phone, which is no use on a wrist, so it's left off.
         */
        fun watchActions(ctx: Context, id: String, first: String, firstLabel: Int): NotificationCompat.WearableExtender =
            NotificationCompat.WearableExtender()
                .addAction(NotificationCompat.Action.Builder(R.drawable.ic_reminder, ctx.getString(firstLabel), ReminderIntents.action(ctx, id, first)).build())
                .addAction(
                    NotificationCompat.Action.Builder(R.drawable.ic_reminder, ctx.getString(R.string.reminders_action_snooze_10), ReminderIntents.action(ctx, id, ReminderIntents.SNOOZE_10))
                        .build(),
                )

        /** Tasks → Reminders tab. */
        fun openReminders(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(Intent.ACTION_VIEW, DEEP_LINK_REMINDERS.toUri()).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        const val DEEP_LINK_REMINDERS = "klean://tasks/reminders"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderNotifierModule {
    @Binds abstract fun notifier(impl: AndroidReminderNotifier): ReminderNotifier
}
