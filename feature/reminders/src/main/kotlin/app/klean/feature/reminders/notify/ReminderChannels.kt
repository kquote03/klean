package app.klean.feature.reminders.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import app.klean.feature.reminders.R
import app.klean.shared.diet.prompt.PromptFormat

/**
 * Reminders' notification channels, created by the module itself (on reschedule and before posting) so
 * the shared `Channels.kt` stays untouched. Ids never change once shipped.
 *
 * Pings rotate formats like diet check-ins (one channel per format). Alarms ring through a foreground
 * service on [ALARM] (silent channel: the service plays the sound); [ALARM_SOUND] is the fallback when
 * the service can't start, and loops its own alarm sound (FLAG_INSISTENT). Both alarm channels ask to
 * bypass Do Not Disturb, which Android honours once the user grants Klean DND access.
 */
object ReminderChannels {
    const val PING = "reminder_ping"
    const val PING_HEADS_UP = "reminder_heads_up"
    const val PING_VIBRATE = "reminder_vibrate"
    const val PING_QUIET = "reminder_quiet"
    const val ALARM = "reminder_alarm"
    const val ALARM_SOUND = "reminder_alarm_sound"

    val VIBRATE_PATTERN = longArrayOf(0, 80, 120, 80)
    /** Alarm vibration, repeated from index 0 while ringing. */
    val ALARM_VIBRATION = longArrayOf(0, 600, 400, 600, 1200)

    fun forFormat(f: PromptFormat): String = when (f) {
        PromptFormat.STANDARD -> PING
        PromptFormat.HEADS_UP -> PING_HEADS_UP
        PromptFormat.SILENT_VIBRATE -> PING_VIBRATE
        PromptFormat.WIDGET_HIGHLIGHT -> PING_QUIET
    }

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(PING, ctx.getString(R.string.reminders_channel_ping), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(PING_HEADS_UP, ctx.getString(R.string.reminders_channel_heads_up), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(PING_VIBRATE, ctx.getString(R.string.reminders_channel_vibrate), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = VIBRATE_PATTERN
                },
                NotificationChannel(PING_QUIET, ctx.getString(R.string.reminders_channel_quiet), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(ALARM, ctx.getString(R.string.reminders_channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(ALARM_SOUND, ctx.getString(R.string.reminders_channel_alarm_sound), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), alarmAudio)
                    enableVibration(true)
                    vibrationPattern = ALARM_VIBRATION
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }
}
