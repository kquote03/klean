package app.klean.feature.diet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.feature.diet.prompt.PromptLogWriter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Delete intent of a check-in: the user swiped it away, so its outcome is DISMISSED (if still pending). */
@AndroidEntryPoint
class PromptDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var writer: PromptLogWriter
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_LOG_ID) ?: return
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { writer.markDismissed(id) }
            } catch (e: Exception) {
                Log.w("Klean", "marking check-in dismissed failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val EXTRA_LOG_ID = "log_id"
    }
}
