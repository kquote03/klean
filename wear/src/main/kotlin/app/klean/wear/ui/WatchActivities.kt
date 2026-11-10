package app.klean.wear.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.app.RemoteInput
import androidx.wear.input.RemoteInputIntentHelper
import app.klean.wear.R
import app.klean.wear.data.WatchGraph
import app.klean.shared.wear.WatchMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outlives an activity that finishes right after asking: sending takes a moment. */
internal val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Sends [m] to the phone and says, calmly, whether it arrived. */
internal fun sendWithToast(ctx: Context, m: WatchMessage) {
    val app = ctx.applicationContext
    watchScope.launch {
        val ok = withContext(Dispatchers.IO) { WatchGraph.link(app).send(m) }
        Toast.makeText(app, if (ok) R.string.sent_to_phone else R.string.phone_not_reachable, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Capture on the watch: the system voice/keyboard input, then the text goes to the phone's universal capture
 * ([WatchMessage.Capture]; [EXTRA_MODULE] targets a module, e.g. "diet" from the food tile).
 */
class WatchCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val input = RemoteInput.Builder(KEY_TEXT).setLabel(getString(labelFor(intent.getStringExtra(EXTRA_MODULE)))).build()
        val i = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(i, listOf(input))
        runCatching { startActivityForResult(i, REQ) }.onFailure { finish() }
    }

    @Deprecated("Activity result API needs androidx.activity; this activity has no other UI")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val text = data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_TEXT)?.toString() }?.trim()
        if (requestCode == REQ && !text.isNullOrEmpty()) {
            sendWithToast(this, captureMessage(text, intent.getStringExtra(EXTRA_MODULE)))
        }
        finish()
    }

    companion object {
        const val EXTRA_MODULE = "module"
        const val KEY_TEXT = "text"
        private const val REQ = 1

        /** Food from the food tile or complication is logged as food; anything else goes through routing. */
        fun captureMessage(text: String, module: String?): WatchMessage =
            if (module == "diet") WatchMessage.LogFood(text) else WatchMessage.Capture(text, module, viaVoice = true)

        fun labelFor(module: String?): Int = when (module) {
            "diet" -> R.string.capture_label_food
            "workout" -> R.string.capture_label_set
            else -> R.string.capture_label
        }
    }
}

/** No UI: a tile chip's one-tap action (log a recent food, start a saved workout). */
class SendActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageFor(intent.getStringExtra(EXTRA_KIND), intent.getStringExtra(EXTRA_VALUE))?.let { sendWithToast(this, it) }
        finish()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_VALUE = "value"
        const val KIND_FOOD = "food"
        const val KIND_WORKOUT = "workout"

        fun messageFor(kind: String?, value: String?): WatchMessage? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return when (kind) {
                KIND_FOOD -> WatchMessage.LogFood(v)
                KIND_WORKOUT -> WatchMessage.StartWorkout(v)
                else -> null
            }
        }
    }
}
