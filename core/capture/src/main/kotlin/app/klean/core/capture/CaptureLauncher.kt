package app.klean.core.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId

/**
 * Opens the capture sheet from a screen inside the app. The app shell provides one that shows the sheet in place,
 * in the app's own activity (plan 2026-11-11, D3): starting [CaptureActivity] (its own task) from inside the app
 * made Back a cross-task return, drawn by predictive back as the whole window zooming out.
 */
fun interface CaptureLauncher {
    fun open(target: ModuleId?, source: CaptureSource, voice: Boolean, eatenAt: Long?)
}

/** Provided by the app shell; null outside it (previews, a module tested alone). */
val LocalCaptureLauncher = staticCompositionLocalOf<CaptureLauncher?> { null }

/** The shell's in-place sheet, or [CaptureActivity] when there is no shell around. */
@Composable
fun rememberCaptureLauncher(): CaptureLauncher {
    val shell = LocalCaptureLauncher.current
    val ctx = LocalContext.current
    return shell ?: remember(ctx) {
        CaptureLauncher { target, source, voice, eatenAt ->
            ctx.startActivity(CaptureActivity.intent(ctx, source, target, startVoice = voice, eatenAt = eatenAt))
        }
    }
}
