package app.klean.core.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.klean.core.design.theme.KleanTheme
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.AndroidEntryPoint

/**
 * Translucent capture sheet over whatever is on screen (QS tile, widget, notification "Capture…").
 * Own task affinity and excluded from recents, so logging never opens the full app.
 */
@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {
    private val vm: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KleanTheme { CaptureSheet(vm = vm, onDismiss = ::finish) } }
    }

    companion object {
        /** Module id string (e.g. "diet"); absent = universal capture. */
        const val EXTRA_TARGET_MODULE = "target_module"
        const val EXTRA_START_VOICE = "start_voice"
        /** [CaptureSource] name. */
        const val EXTRA_SOURCE = "source"

        /** Used when a caller passes no source, e.g. a manual `adb shell am start`. */
        val DEFAULT_SOURCE = CaptureSource.DASHBOARD

        fun intent(context: Context, source: CaptureSource, target: ModuleId? = null, startVoice: Boolean = false): Intent =
            Intent(context, CaptureActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.name)
                .putExtra(EXTRA_START_VOICE, startVoice)
                .apply { target?.let { putExtra(EXTRA_TARGET_MODULE, it.value) } }
    }
}
