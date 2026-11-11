package app.klean.nav

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Screen-to-screen motion (docs/ui/design-system.md): Material's shared axis X, 250 ms with emphasized easing.
 * With reduced motion (the system animator scale at 0, "Remove animations"), a 100 ms cross-fade and no movement.
 */
class KleanMotion(private val reduced: Boolean) {
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** [forward]: a new screen comes in from the end; back: from the start. */
    fun enter(forward: Boolean): EnterTransition =
        if (reduced) {
            fadeIn(tween(FADE_MS))
        } else {
            slideInHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) w / SHIFT else -w / SHIFT } +
                fadeIn(tween(AXIS_MS / 2, delayMillis = AXIS_MS / 5))
        }

    fun exit(forward: Boolean): ExitTransition =
        if (reduced) {
            fadeOut(tween(FADE_MS))
        } else {
            slideOutHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) -w / SHIFT else w / SHIFT } +
                fadeOut(tween(AXIS_MS / 5))
        }

    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(forward = true) }
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(forward = true) }
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(forward = false) }
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(forward = false) }

    companion object {
        const val AXIS_MS = 250
        const val FADE_MS = 100

        /** The incoming screen starts this fraction of the width away (Material uses 30 dp; a tenth reads the same). */
        private const val SHIFT = 10

        /** Reduced motion is the system animator duration scale set to 0. */
        fun isReduced(animatorScale: Float): Boolean = animatorScale == 0f

        fun from(context: Context): KleanMotion =
            KleanMotion(isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)))
    }
}
