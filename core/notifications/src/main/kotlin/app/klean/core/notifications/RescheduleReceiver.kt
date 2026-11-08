package app.klean.core.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Re-arms every alarm after the events that clear or skew them: boot, app update, a clock or time
 * zone change, and a change in the exact-alarm permission. Exported because these are system
 * broadcasts; anything else is ignored.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var rescheduler: Rescheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in SYSTEM_ACTIONS && action != debugAction(context)) return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                ChannelRegistrar.ensure(context)
                withTimeout(TIMEOUT_MS) { rescheduler.rescheduleAll() }
            } catch (e: Exception) {
                Log.w("Klean", "reschedule after $action failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000L

        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )

        /** Debug builds also accept `<applicationId>.RESCHEDULE` (declared in the app's debug manifest). */
        fun debugAction(context: Context) = context.packageName + ".RESCHEDULE"
    }
}
