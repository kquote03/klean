package app.klean.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes

/** Notification channel ids. Ids never change once shipped; names come from strings.xml. */
object Channels {
    const val DIET_STATUS = "diet_status"
    const val DIET_PROMPT = "diet_prompt"
    const val DIET_PROMPT_HEADS_UP = "diet_prompt_heads_up"
    const val DIET_PROMPT_VIBRATE = "diet_prompt_vibrate"
    const val UNLOCK_SHEET = "unlock_sheet"
    const val BACKUP = "backup"

    /** Short, gentle vibration for the vibrate-only check-in channel. */
    val VIBRATE_PATTERN = longArrayOf(0, 80, 120, 80)
}

/** Fixed notification ids, one per kind, so each kind replaces itself instead of stacking. */
object NotificationIds {
    const val DIET_STATUS = 1001
    const val DIET_PROMPT = 1002
    const val UNLOCK = 1003
    const val DELIVERY = 1004
    const val GEOFENCE = 1005
    const val BACKUP = 1006
}

/** Creates every channel. Idempotent: safe to call on each app start (names follow the locale). */
object ChannelRegistrar {
    internal data class Spec(val id: String, @StringRes val name: Int, val importance: Int, val vibrateOnly: Boolean = false)

    internal val specs = listOf(
        Spec(Channels.DIET_STATUS, R.string.channel_diet_status, NotificationManager.IMPORTANCE_LOW),
        Spec(Channels.DIET_PROMPT, R.string.channel_diet_prompt, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(Channels.DIET_PROMPT_HEADS_UP, R.string.channel_diet_prompt_heads_up, NotificationManager.IMPORTANCE_HIGH),
        Spec(Channels.DIET_PROMPT_VIBRATE, R.string.channel_diet_prompt_vibrate, NotificationManager.IMPORTANCE_DEFAULT, vibrateOnly = true),
        Spec(Channels.UNLOCK_SHEET, R.string.channel_unlock_sheet, NotificationManager.IMPORTANCE_HIGH),
        Spec(Channels.BACKUP, R.string.channel_backup, NotificationManager.IMPORTANCE_LOW),
    )

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            specs.map { s ->
                NotificationChannel(s.id, context.getString(s.name), s.importance).apply {
                    if (s.vibrateOnly) {
                        setSound(null, null)
                        enableVibration(true)
                        vibrationPattern = Channels.VIBRATE_PATTERN
                    }
                    if (s.id == Channels.DIET_STATUS) setShowBadge(false)
                }
            },
        )
    }
}
