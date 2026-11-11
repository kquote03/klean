package app.klean.core.capture.voice

import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import kotlinx.serialization.Serializable

/**
 * The full-screen voice capture. [module] aims it (its chip starts selected), e.g. `klean://voice?module=diet`.
 * Full screen, so the shell hides the navigation bar on it, as on the account menu.
 */
@Serializable
data class VoiceRoute(val module: String? = null) {
    companion object {
        const val ARG_MODULE = "module"
        const val LINK = "klean://voice"
    }
}

/**
 * Registers [VoiceRoute] (one line in the app's NavHost). It slides up from the bottom and back down on exit
 * (plan 2026-11-11 decision 6); with animations off it cross-fades in 100 ms.
 */
fun NavGraphBuilder.voiceScreen(nav: NavController) {
    fun reduced() = Settings.Global.getFloat(nav.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    composable<VoiceRoute>(
        deepLinks = listOf(navDeepLink<VoiceRoute>(basePath = VoiceRoute.LINK)),
        enterTransition = { if (reduced()) fadeIn(tween(100)) else slideInVertically { it } + fadeIn() },
        exitTransition = { fadeOut(tween(100)) },
        popEnterTransition = { fadeIn(tween(100)) },
        popExitTransition = { if (reduced()) fadeOut(tween(100)) else slideOutVertically { it } + fadeOut() },
    ) {
        // Pops only this screen, even if Close and the end of "Saved" race.
        VoiceCaptureScreen(hiltViewModel(), onClose = { nav.popBackStack<VoiceRoute>(inclusive = true) })
    }
}
