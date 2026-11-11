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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/** How two screens relate, which decides how one replaces the other (docs/ui/design-system.md, Motion). */
enum class MotionKind {
    /** Between top-level destinations (Today, the bottom-bar groups, a group's module tabs): fade through. */
    FADE_THROUGH,

    /** Parent → child and back: shared axis X. */
    AXIS_X,

    /** A full-screen page (the account menu, the voice screen): slides up over the screen, down on the way out. */
    SLIDE_UP,
}

/** Full-screen pages: they slide up from the bottom and back down. The voice screen's route joins this set. */
val FULL_SCREEN_ROUTES: Set<KClass<*>> = setOf(AccountRoute::class)

/** The motion between [from] and [to] (either order: Back reverses the same motion). Unknown screens use the axis. */
fun motionKind(
    from: KClass<*>?,
    to: KClass<*>?,
    topLevel: Set<KClass<*>>,
    fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
): MotionKind = motionKind(
    fromTop = from in topLevel, toTop = to in topLevel,
    fromFull = from in fullScreen, toFull = to in fullScreen,
)

private fun motionKind(fromTop: Boolean, toTop: Boolean, fromFull: Boolean, toFull: Boolean): MotionKind = when {
    fromFull || toFull -> MotionKind.SLIDE_UP
    fromTop && toTop -> MotionKind.FADE_THROUGH
    else -> MotionKind.AXIS_X
}

/**
 * Screen-to-screen motion, picked by how the two screens relate ([MotionKind], plan 2026-11-11 E3):
 * - fade-through between top-level destinations: 90 ms out, 210 ms in with a 0.92 → 1 scale;
 * - Material's shared axis X into a detail and back: 250 ms, emphasized easing;
 * - full-screen pages slide up, and down on the way out.
 *
 * With reduced motion (the system animator scale at 0, "Remove animations"), a 100 ms cross-fade and no movement
 * for all of them. NavHost also plays the pop transitions under predictive back, so a Back gesture inside the app
 * shows the screen underneath, never the system's shrinking window (that is right only on Today, leaving the app).
 */
class KleanMotion(
    private val reduced: Boolean,
    private val topLevel: Set<KClass<*>> = emptySet(),
    private val fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
) {
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Shared axis X. [forward]: a new screen comes in from the end; back: from the start. */
    fun enter(forward: Boolean): EnterTransition = enter(MotionKind.AXIS_X, forward)

    fun exit(forward: Boolean): ExitTransition = exit(MotionKind.AXIS_X, forward)

    /** The incoming screen's motion; [forward] = a push, not a pop. */
    fun enter(kind: MotionKind, forward: Boolean): EnterTransition = when {
        reduced -> fadeIn(tween(FADE_MS))
        kind == MotionKind.FADE_THROUGH ->
            fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) +
                scaleIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS), initialScale = FADE_SCALE)
        // The page rises over the screen; on the way back the screen is simply there under it.
        kind == MotionKind.SLIDE_UP ->
            if (forward) slideInVertically(tween(AXIS_MS, easing = emphasized)) { it } + fadeIn(tween(AXIS_MS / 2)) else EnterTransition.None
        else ->
            slideInHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) w / SHIFT else -w / SHIFT } +
                fadeIn(tween(AXIS_MS / 2, delayMillis = AXIS_MS / 5))
    }

    /** The outgoing screen's motion; [forward] = a push, not a pop. */
    fun exit(kind: MotionKind, forward: Boolean): ExitTransition = when {
        reduced -> fadeOut(tween(FADE_MS))
        kind == MotionKind.FADE_THROUGH -> fadeOut(tween(FADE_OUT_MS))
        // Under a rising page the screen stays put; the page itself slides back down.
        kind == MotionKind.SLIDE_UP ->
            if (forward) {
                // Held fully visible for as long as the page takes to rise.
                fadeOut(tween(AXIS_MS), targetAlpha = 1f)
            } else {
                slideOutVertically(tween(AXIS_MS, easing = emphasized)) { it } + fadeOut(tween(AXIS_MS / 2, delayMillis = AXIS_MS / 2))
            }
        else ->
            slideOutHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) -w / SHIFT else w / SHIFT } +
                fadeOut(tween(AXIS_MS / 5))
    }

    private fun NavDestination.isIn(set: Set<KClass<*>>) = set.any { hasRoute(it) }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind(): MotionKind {
        val from = initialState.destination
        val to = targetState.destination
        return motionKind(
            fromTop = from.isIn(topLevel), toTop = to.isIn(topLevel),
            fromFull = from.isIn(fullScreen), toFull = to.isIn(fullScreen),
        )
    }

    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(kind(), forward = true) }
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(kind(), forward = true) }
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { enter(kind(), forward = false) }
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { exit(kind(), forward = false) }

    companion object {
        const val AXIS_MS = 250
        const val FADE_MS = 100
        const val FADE_OUT_MS = 90
        const val FADE_IN_MS = 210
        const val FADE_SCALE = 0.92f

        /** The incoming screen starts this fraction of the width away (Material uses 30 dp; a tenth reads the same). */
        private const val SHIFT = 10

        /** Reduced motion is the system animator duration scale set to 0. */
        fun isReduced(animatorScale: Float): Boolean = animatorScale == 0f

        fun from(context: Context, topLevel: Set<KClass<*>> = emptySet(), fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES): KleanMotion =
            KleanMotion(
                isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)),
                topLevel,
                fullScreen,
            )
    }
}
