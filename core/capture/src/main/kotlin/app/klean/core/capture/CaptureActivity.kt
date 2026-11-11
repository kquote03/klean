package app.klean.core.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.ui.theme.KleanThemeFromPrefs
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Translucent capture sheet over whatever is on screen (QS tile, widget, notification "Capture…").
 * Own task affinity and excluded from recents, so logging never opens the full app.
 */
@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {
    private val vm: CaptureViewModel by viewModels()
    @Inject lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the view model reads the extras: a deep link or Assistant/shortcut intent becomes plain extras.
        intent = normalize(intent)
        super.onCreate(savedInstanceState)
        // The same entry point as the in-app sheet; after a recreation the view model already holds it.
        if (savedInstanceState == null) vm.startFrom(intent)
        enableEdgeToEdge()
        setContent { KleanThemeFromPrefs(prefs) { CaptureSheet(vm = vm, onDismiss = ::finish) } }
    }

    companion object {
        /** Prefilled text (deep link, shortcut, Assistant). Never saved without the person tapping Save. */
        const val EXTRA_TEXT = "prefill_text"
        /** Module id string (e.g. "diet"); absent = universal capture. */
        const val EXTRA_TARGET_MODULE = "target_module"
        const val EXTRA_START_VOICE = "start_voice"
        /** [CaptureSource] name. */
        const val EXTRA_SOURCE = "source"
        /** UTC ms the entry is for (e.g. the middle of a gap); absent = now. */
        const val EXTRA_EATEN_AT = "eaten_at"

        /** Used when a caller passes no source, e.g. a manual `adb shell am start`. */
        val DEFAULT_SOURCE = CaptureSource.DASHBOARD

        /** Aims [this] sheet from an intent's extras (after [normalize]). */
        fun CaptureViewModel.startFrom(intent: Intent) = start(
            target = intent.getStringExtra(EXTRA_TARGET_MODULE)?.let(::ModuleId),
            source = intent.getStringExtra(EXTRA_SOURCE)?.let { runCatching { CaptureSource.valueOf(it) }.getOrNull() }
                ?: DEFAULT_SOURCE,
            voice = intent.getBooleanExtra(EXTRA_START_VOICE, false),
            text = intent.getStringExtra(EXTRA_TEXT),
            eatenAt = if (intent.hasExtra(EXTRA_EATEN_AT)) intent.getLongExtra(EXTRA_EATEN_AT, 0L) else null,
        )

        /**
         * `klean://capture?text=…&module=…` and App Actions extras (`text`, `module`) → this activity's extras.
         * A link with no source counts as [CaptureSource.ASSISTANT] (shortcuts, Assistant, other apps).
         */
        fun normalize(intent: Intent): Intent {
            val data = intent.dataString
            val fromLink = CaptureLinks.isCaptureLink(data) ||
                intent.hasExtra(CaptureLinks.EXTRA_TEXT) || intent.hasExtra(CaptureLinks.EXTRA_MODULE)
            if (!fromLink) return intent
            val link = CaptureLinks.parse(
                data,
                extraText = intent.getStringExtra(CaptureLinks.EXTRA_TEXT),
                extraModule = intent.getStringExtra(CaptureLinks.EXTRA_MODULE),
            )
            return Intent(intent).apply {
                link.text?.let { putExtra(EXTRA_TEXT, it) }
                if (!hasExtra(EXTRA_TARGET_MODULE)) link.module?.let { putExtra(EXTRA_TARGET_MODULE, it.value) }
                if (!hasExtra(EXTRA_SOURCE)) putExtra(EXTRA_SOURCE, CaptureSource.ASSISTANT.name)
            }
        }

        fun intent(
            context: Context,
            source: CaptureSource,
            target: ModuleId? = null,
            startVoice: Boolean = false,
            eatenAt: Long? = null,
        ): Intent =
            Intent(context, CaptureActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.name)
                .putExtra(EXTRA_START_VOICE, startVoice)
                .apply {
                    target?.let { putExtra(EXTRA_TARGET_MODULE, it.value) }
                    eatenAt?.let { putExtra(EXTRA_EATEN_AT, it) }
                }
    }
}
