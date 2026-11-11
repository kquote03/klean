package app.klean.nav

import app.klean.feature.tasks.TaskEditRoute
import app.klean.feature.tasks.TasksRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion by relationship (plan 2026-11-11, E3): shared axis X into a detail and back, slide up for full-screen pages.
 * Top-level pages move in the shell's pager, not by navigation.
 */
class MotionKindTest {
    @Test fun childIsSharedAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = TasksRoute::class, to = TaskEditRoute::class))

    @Test fun settingsFromThePagesIsSharedAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = PagesRoute::class, to = SettingsRoute::class))

    @Test fun accountAndVoiceSlideUp() {
        assertEquals(MotionKind.SLIDE_UP, motionKind(from = PagesRoute::class, to = AccountRoute::class))
        // Leaving it (Back) slides it down: the same kind, seen from the other side.
        assertEquals(MotionKind.SLIDE_UP, motionKind(from = AccountRoute::class, to = PagesRoute::class))
    }

    @Test fun unknownDestinationsUseTheAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = null, to = TasksRoute::class))

    @Test fun noPageSlideIsLeft() = assertEquals(listOf(MotionKind.AXIS_X, MotionKind.SLIDE_UP), MotionKind.entries)

    @Test fun reducedMotionIsOneCrossFadeForEveryKind() {
        val m = KleanMotion(reduced = true)
        MotionKind.entries.forEach { k ->
            assertEquals(androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(KleanMotion.FADE_MS)), m.enter(k, forward = true))
            assertEquals(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(KleanMotion.FADE_MS)), m.exit(k, forward = false))
        }
    }

    @Test fun eachKindLooksDifferent() {
        val m = KleanMotion(reduced = false)
        val enters = MotionKind.entries.map { m.enter(it, forward = true) }
        assertEquals(enters.size, enters.toSet().size)
    }
}
