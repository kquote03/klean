package app.klean.feature.diet.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.klean.core.common.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Listens for SCREEN_OFF / SCREEN_ON / USER_PRESENT (plan §6.3). These broadcasts can't be declared
 * in the manifest, so the receiver is registered at runtime from `KleanApp.onCreate` and lives as long
 * as the process. No foreground service: nothing here needs to run continuously, and "watch the
 * screen" is not a valid FGS type. If the process is killed, events are lost until something brings
 * it back (a check-in alarm, the widget, the tile, a notification action); [UnlockSheetTrigger.onProcessStart]
 * then counts the break from that moment.
 */
@Singleton
class ScreenStateMonitor @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val trigger: UnlockSheetTrigger,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val pending = goAsync()
            scope.launch {
                try {
                    when (action) {
                        Intent.ACTION_SCREEN_OFF -> trigger.onScreenOff()
                        // Screen on (keyguard up or not) and the unlock both count; the trigger shows one sheet.
                        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> trigger.onWake()
                    }
                } catch (e: Exception) {
                    Log.w("Klean", "unlock sheet: $action failed", e)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // System broadcasts still arrive with NOT_EXPORTED; other apps can't send these to us.
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val interactive = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        scope.launch { runCatching { trigger.onProcessStart(interactive) } }
    }
}
