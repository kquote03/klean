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

/** How two screens relate, which decides how one replaces the other (docs/ui/design-system.md, Motion). */
enum class MotionKind {
    /** Between top-level pages (Today, the bottom-bar groups, a group's module tabs): a full-width slide, by page order. */
    PAGE,

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
    topLevel: Set<KClass<*>>,
    fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
): MotionKind = motionKind(
    fromTop = from in topLevel, toTop = to in topLevel,
    fromFull = from in fullScreen, toFull = to in fullScreen,
)

private fun motionKind(fromTop: Boolean, toTop: Boolean, fromFull: Boolean, toFull: Boolean): MotionKind = when {
    fromFull || toFull -> MotionKind.SLIDE_UP
    fromTop && toTop -> MotionKind.PAGE
    else -> MotionKind.AXIS_X
}

/**
 * Whether the motion runs forward (the new screen comes in from the right). A [MotionKind.PAGE] follows the page
 * [order] whether it is a push or a pop, so Back from Track to Today brings Today in from the left; everything else
 * follows [push]. A page outside the order (a module that is off) falls back to [push].
 */
fun motionForward(order: List<KClass<*>>, from: KClass<*>?, to: KClass<*>?, kind: MotionKind, push: Boolean): Boolean =
    if (kind == MotionKind.PAGE) pageForward(order, from, to) ?: push else push

/**
 * Screen-to-screen motion, picked by how the two screens relate ([MotionKind], plan 2026-11-11 E3):
 * - a full-width horizontal slide between top-level pages, 300 ms with emphasized easing, its direction from
 *   [pageOrder] (the swipe order: Today, Plan's tabs, Track's tabs, People's tabs);
 * - Material's shared axis X into a detail and back: 250 ms, emphasized easing;
 * - full-screen pages slide up, and down on the way out.
 *
 * With reduced motion (the system animator scale at 0, "Remove animations"), a 100 ms cross-fade and no movement
 * for all of them. NavHost also plays the pop transitions under predictive back, so a Back gesture inside the app
 * shows the screen underneath sliding in, never a zoom (the fade-through's scale read as one under predictive back).
 */
class KleanMotion(
    private val reduced: Boolean,
    private val topLevel: Set<KClass<*>> = emptySet(),
    private val fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
    private val pageOrder: () -> List<KClass<*>> = { emptyList() },
) {
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Shared axis X. [forward]: a new screen comes in from the end; back: from the start. */
    fun enter(forward: Boolean): EnterTransition = enter(MotionKind.AXIS_X, forward)

    fun exit(forward: Boolean): ExitTransition = exit(MotionKind.AXIS_X, forward)

    /** The incoming screen's motion; [forward] = a push (for a page: towards the right in page order). */
    fun enter(kind: MotionKind, forward: Boolean): EnterTransition = when {
        reduced -> fadeIn(tween(FADE_MS))
        kind == MotionKind.PAGE ->
            slideInHorizontally(tween(PAGE_MS, easing = emphasized)) { w -> if (forward) w else -w }
        // The page rises over the screen; on the way back the screen is simply there under it.
        kind == MotionKind.SLIDE_UP ->
            if (forward) slideInVertically(tween(AXIS_MS, easing = emphasized)) { it } + fadeIn(tween(AXIS_MS / 2)) else EnterTransition.None
        else ->
            slideInHorizontally(tween(AXIS_MS, easing = emphasized)) { w -> if (forward) w / SHIFT else -w / SHIFT } +
                fadeIn(tween(AXIS_MS / 2, delayMillis = AXIS_MS / 5))
    }

    /** The outgoing screen's motion; [forward] = a push (for a page: towards the right in page order). */
    fun exit(kind: MotionKind, forward: Boolean): ExitTransition = when {
        reduced -> fadeOut(tween(FADE_MS))
        kind == MotionKind.PAGE ->
            slideOutHorizontally(tween(PAGE_MS, easing = emphasized)) { w -> if (forward) -w else w }
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

    private fun NavDestination.pageIn(order: List<KClass<*>>): KClass<*>? = order.firstOrNull { hasRoute(it) }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind(): MotionKind {
        val from = initialState.destination
        val to = targetState.destination
        return motionKind(
            fromTop = from.isIn(topLevel), toTop = to.isIn(topLevel),
            fromFull = from.isIn(fullScreen), toFull = to.isIn(fullScreen),
        )
    }

    /** [push] for a push, adjusted to the page order for a page slide. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.forward(kind: MotionKind, push: Boolean): Boolean {
        if (kind != MotionKind.PAGE) return push
        val order = pageOrder()
        return motionForward(order, initialState.destination.pageIn(order), targetState.destination.pageIn(order), kind, push)
    }

    val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { kind().let { enter(it, forward(it, push = true)) } }
    val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { kind().let { exit(it, forward(it, push = true)) } }
    val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { kind().let { enter(it, forward(it, push = false)) } }
    val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { kind().let { exit(it, forward(it, push = false)) } }

    companion object {
        const val AXIS_MS = 250
        const val PAGE_MS = 300
        const val FADE_MS = 100

        /** The incoming screen starts this fraction of the width away (Material uses 30 dp; a tenth reads the same). */
        private const val SHIFT = 10

        /** Reduced motion is the system animator duration scale set to 0. */
        fun isReduced(animatorScale: Float): Boolean = animatorScale == 0f

        fun from(
            context: Context,
            topLevel: Set<KClass<*>> = emptySet(),
            fullScreen: Set<KClass<*>> = FULL_SCREEN_ROUTES,
            pageOrder: () -> List<KClass<*>> = { emptyList() },
        ): KleanMotion =
            KleanMotion(
                isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)),
                topLevel,
                fullScreen,
                pageOrder,
            )
    }
}
