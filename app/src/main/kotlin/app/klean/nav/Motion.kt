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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/**
 * How two screens relate, which decides how one replaces the other (docs/ui/design-system.md, Motion). Moving between
 * top-level pages isn't navigation any more: the shell's pager moves them under the finger.
 */
enum class MotionKind {
    /** Parent → child and back: shared axis X. */
    AXIS_X,

    /** A full-screen page (the account menu, the voice screen): slides up over the screen, down on the way out. */
    SLIDE_UP,
}

/** Full-screen pages: they slide up from the bottom and back down. The voice screen's route joins this set. */
val FULL_SCREEN_ROUTES: Set<KClass<*>> = setOf(AccountRoute::class, app.klean.core.capture.voice.VoiceRoute::class)

/** The motion between [from] and [to] (either order: Back reverses the same motion). Unknown screens use the axis. */
fun motionKind(
    from: KClass<*>?,
    to: KClass<*>?,
    fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
): MotionKind = motionKind(fromFull = from in fullScreen, toFull = to in fullScreen)

private fun motionKind(fromFull: Boolean, toFull: Boolean): MotionKind =
    if (fromFull || toFull) MotionKind.SLIDE_UP else MotionKind.AXIS_X

/**
 * Screen-to-screen motion, picked by how the two screens relate ([MotionKind], plan 2026-11-11 E3):
 * - Material's shared axis X into a detail and back: 250 ms, emphasized easing;
 * - full-screen pages slide up, and down on the way out.
 *
 * With reduced motion (the system animator scale at 0, "Remove animations"), a 100 ms cross-fade and no movement
 * for all of them. NavHost also plays the pop transitions under predictive back, so a Back gesture inside the app
 * shows the screen underneath sliding in, never a zoom (the fade-through's scale read as one under predictive back).
 */
class KleanMotion(
    private val reduced: Boolean,
    private val fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
    /**
     * Routes the shell turns straight into a pager page (Today, Track's Week, the modules' start routes): reaching one
     * pops back to the pages at once, so neither step animates.
     */
    private val instant: Set<KClass<*>> = emptySet(),
) {
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Shared axis X. [forward]: a new screen comes in from the end; back: from the start. */
    fun enter(forward: Boolean): EnterTransition = enter(MotionKind.AXIS_X, forward)

    fun exit(forward: Boolean): ExitTransition = exit(MotionKind.AXIS_X, forward)

    /** The incoming screen's motion; [forward] = a push. */
    fun enter(kind: MotionKind, forward: Boolean): EnterTransition = when {
        reduced -> fadeIn(tween(FADE_MS))
        // The page rises over the screen; on the way back the screen is simply there under it.
        kind == MotionKind.SLIDE_UP ->
            if (forward) slideInVertically(tween(AXIS_MS, easing = emphasized)) { it } + fadeIn(tween(AXIS_MS / 2)) else EnterTransition.None
        else ->
            slideInHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) w / SHIFT else -w / SHIFT } +
                fadeIn(tween(AXIS_MS / 2, delayMillis = AXIS_MS / 5))
    }

    /** The outgoing screen's motion; [forward] = a push. */
    fun exit(kind: MotionKind, forward: Boolean): ExitTransition = when {
        reduced -> fadeOut(tween(FADE_MS))
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

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind(): MotionKind =
        motionKind(fromFull = initialState.destination.isIn(fullScreen), toFull = targetState.destination.isIn(fullScreen))

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isInstant(): Boolean =
        initialState.destination.isIn(instant) || targetState.destination.isIn(instant)

    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { if (isInstant()) EnterTransition.None else enter(kind(), forward = true) }
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { if (isInstant()) ExitTransition.None else exit(kind(), forward = true) }
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { if (isInstant()) EnterTransition.None else enter(kind(), forward = false) }
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { if (isInstant()) ExitTransition.None else exit(kind(), forward = false) }

    companion object {
        const val AXIS_MS = 250
        const val FADE_MS = 100

        /** The incoming screen starts this fraction of the width away (Material uses 30 dp; a tenth reads the same). */
        private const val SHIFT = 10

        /** Reduced motion is the system animator duration scale set to 0. */
        fun isReduced(animatorScale: Float): Boolean = animatorScale == 0f

        /** Whether animations are off on this device. */
        fun reducedOn(context: Context): Boolean =
            isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))

        fun from(context: Context, fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES, instant: Set<KClass<*>> = emptySet()): KleanMotion =
            KleanMotion(reducedOn(context), fullScreen, instant)
    }
}
