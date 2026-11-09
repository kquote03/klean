package app.klean.core.capture.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.common.ApplicationScope
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusRefresh
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * "Add something" inline reply from the Today status line. Broadcast → universal capture → Room, with
 * no UI: works from the lock screen without unlocking. Plain text with no other match lands in diet
 * (holistic plan H7), so a food note still saves in one step.
 */
@AndroidEntryPoint
class StatusReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: StatusReplyHandler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    // No super.onReceive: it is abstract; the Hilt Gradle plugin injects before this body runs.
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()
        // Null when onReceive is called directly (tests); the work still runs in the app scope.
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                handler.handle(text)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val KEY_TEXT = "klean_text"
    }
}

/** The save path of [StatusReplyReceiver], kept free of Android receiver plumbing. */
class StatusReplyHandler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val capture: UniversalCaptureService,
    private val registry: ModuleRegistry,
    private val status: KleanStatusNotifier,
    private val refresh: StatusRefresh,
    private val clock: Clock,
) {
    suspend fun handle(rawText: String?) {
        val text = rawText?.trim()?.takeIf { it.isNotEmpty() }
        try {
            withTimeout(TIMEOUT_MS) {
                val saved = text?.let {
                    capture.submit(CaptureInput(it, CaptureSource.NOTIFICATION_REPLY, clock.now().toEpochMilliseconds()))
                } as? CaptureResult.Saved
                val label = saved?.let { s -> registry.all.firstOrNull { it.id == s.moduleId }?.let { ctx.getString(it.labelRes) } }
                // Re-posting is also what clears the reply spinner, so it happens even for a blank reply.
                status.refresh(savedTo = label)
                runCatching { refresh.refresh() }
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Log.w("Klean", "status reply save timed out", e)
            status.refresh()
        } catch (e: Exception) {
            Log.w("Klean", "status reply save failed", e)
            status.refresh()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000L
    }
}
