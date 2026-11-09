package app.klean.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes

/** Notification channel ids. Ids never change once shipped; names come from strings.xml. */
object Channels {
    /**
     * v2: DEFAULT importance, silenced at the channel. Pixel hides IMPORTANCE_LOW notifications from
     * the lock screen by default, which defeated the lock-screen count. Importance can't be raised
     * on an existing channel, hence the new id. v1 of the holistic "Today" status (M16) replaced
     * diet_status_v2 with the same settings.
     */
    const val STATUS = "status_v1"
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
    /** The holistic "Today" status line (was the diet status, same id). */
    const val STATUS = 1001
    const val DIET_PROMPT = 1002
    const val UNLOCK = 1003
    const val DELIVERY = 1004
    const val GEOFENCE = 1005
    const val BACKUP = 1006
}

/** Creates every channel. Idempotent: safe to call on each app start (names follow the locale). */
object ChannelRegistrar {
    internal data class Spec(
        val id: String,
        @StringRes val name: Int,
        val importance: Int,
        val vibrateOnly: Boolean = false,
        val silent: Boolean = false,
    )

    /** Ids replaced by a newer channel; deleted so they don't linger in system settings. */
    internal val retired = listOf("diet_status", "diet_status_v2")

    internal val specs = listOf(
        Spec(Channels.STATUS, R.string.channel_status, NotificationManager.IMPORTANCE_DEFAULT, silent = true),
        Spec(Channels.DIET_PROMPT, R.string.channel_diet_prompt, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(Channels.DIET_PROMPT_HEADS_UP, R.string.channel_diet_prompt_heads_up, NotificationManager.IMPORTANCE_HIGH),
        Spec(Channels.DIET_PROMPT_VIBRATE, R.string.channel_diet_prompt_vibrate, NotificationManager.IMPORTANCE_DEFAULT, vibrateOnly = true),
        Spec(Channels.UNLOCK_SHEET, R.string.channel_unlock_sheet, NotificationManager.IMPORTANCE_HIGH),
        Spec(Channels.BACKUP, R.string.channel_backup, NotificationManager.IMPORTANCE_LOW),
    )

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        retired.forEach(nm::deleteNotificationChannel)
        nm.createNotificationChannels(
            specs.map { s ->
                NotificationChannel(s.id, context.getString(s.name), s.importance).apply {
                    if (s.vibrateOnly) {
                        setSound(null, null)
                        enableVibration(true)
                        vibrationPattern = Channels.VIBRATE_PATTERN
                    }
                    if (s.silent) {
                        setSound(null, null)
                        enableVibration(false)
                    }
                    if (s.id == Channels.STATUS) setShowBadge(false)
                }
            },
        )
    }
}
