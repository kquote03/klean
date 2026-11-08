package app.klean.feature.diet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import app.klean.core.common.ApplicationScope
import app.klean.feature.diet.data.DietRepository
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Inline "Log food" reply from the status or prompt notification (plan §5.2, §6.1).
 * Broadcast → repository → Room, with no UI: works from the lock screen without unlocking.
 */
@AndroidEntryPoint
class DietReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: DietReplyHandler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    // No super.onReceive: it is abstract; the Hilt Gradle plugin injects before this body runs.
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()
        val source = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
            ?: CaptureSource.NOTIFICATION_REPLY
        // Null when onReceive is called directly (tests); the work still runs in the app scope.
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                handler.handle(text, source)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val KEY_TEXT = "klean_text"
        const val EXTRA_SOURCE = "source"
    }
}

/** The save path of [DietReplyReceiver], kept free of Android receiver plumbing. */
class DietReplyHandler @Inject constructor(
    private val repo: DietRepository,
    private val status: DietStatusNotifier,
) {
    suspend fun handle(rawText: String?, source: CaptureSource) {
        val text = rawText?.trim()?.takeIf { it.isNotEmpty() }
        try {
            withTimeout(TIMEOUT_MS) {
                // logEntry awaits the dispatcher, so widget and prompts are current before we confirm.
                if (text != null) repo.logEntry(text, source)
                // Re-posting is also what clears the reply spinner, so it happens even for a blank reply.
                status.refresh(savedText = text)
            }
        } catch (e: CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Log.w("Klean", "reply save timed out", e)
            status.refresh()
        } catch (e: Exception) {
            Log.w("Klean", "reply save failed", e)
            status.refresh()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
