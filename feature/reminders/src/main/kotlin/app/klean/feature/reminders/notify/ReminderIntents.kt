package app.klean.feature.reminders.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Broadcasts behind the notification buttons. Each (reminder, action) pair is its own PendingIntent. */
object ReminderIntents {
    const val DONE = "app.klean.reminders.DONE"
    const val DISMISS = "app.klean.reminders.DISMISS"
    const val SNOOZE_10 = "app.klean.reminders.SNOOZE_10"
    const val SNOOZE_HOUR = "app.klean.reminders.SNOOZE_HOUR"
    const val SNOOZE_TOMORROW = "app.klean.reminders.SNOOZE_TOMORROW"
    /** The notification was swiped away: nothing changes, the format rotation learns from it. */
    const val SWIPED = "app.klean.reminders.SWIPED"
    const val EXTRA_ID = "reminderId"

    fun intent(ctx: Context, id: String, action: String): Intent = Intent(ctx, ReminderActionReceiver::class.java)
        .setAction(action)
        .setData("klean-reminder://$id/${action.substringAfterLast('.')}".toUri())
        .putExtra(EXTRA_ID, id)

    fun action(ctx: Context, id: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, intent(ctx, id, action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
