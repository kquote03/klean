package app.klean.core.capture.status

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
 * Delete intent of the status notification. Android 14+ lets users swipe ongoing notifications away;
 * we re-post silently. The real off switches are the status setting and the system channel toggle,
 * both of which [KleanStatusNotifier.refresh] respects.
 */
@AndroidEntryPoint
class StatusRepostReceiver : BroadcastReceiver() {
    @Inject lateinit var status: KleanStatusNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { status.refresh() }
            } catch (e: Exception) {
                Log.w("Klean", "status re-post failed", e)
            } finally {
                pending?.finish()
            }
        }
    }
}
