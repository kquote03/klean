package app.klean.nav

import app.klean.feature.tasks.TaskEditRoute
import app.klean.feature.tasks.TasksRoute
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion by relationship (plan 2026-11-11, E3): fade-through between top-level destinations, shared axis X into a
 * detail and back, slide up for full-screen pages.
 */
class MotionKindTest {
    private val topLevelRoutes: Set<KClass<*>> = setOf(TodayRoute::class, TrackWeekRoute::class, TasksRoute::class)

    @Test fun groupSwitchIsFadeThrough() =
        assertEquals(MotionKind.FADE_THROUGH, motionKind(from = TodayRoute::class, to = TasksRoute::class, topLevel = topLevelRoutes))

    @Test fun backToTodayIsFadeThroughToo() =
        assertEquals(MotionKind.FADE_THROUGH, motionKind(from = TasksRoute::class, to = TodayRoute::class, topLevel = topLevelRoutes))

    @Test fun childIsSharedAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = TasksRoute::class, to = TaskEditRoute::class, topLevel = topLevelRoutes))

    @Test fun settingsFromTodayIsSharedAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = TodayRoute::class, to = SettingsRoute::class, topLevel = topLevelRoutes))

    @Test fun accountAndVoiceSlideUp() {
        assertEquals(MotionKind.SLIDE_UP, motionKind(from = TodayRoute::class, to = AccountRoute::class, topLevel = topLevelRoutes))
        // Leaving it (Back) slides it down: the same kind, seen from the other side.
        assertEquals(MotionKind.SLIDE_UP, motionKind(from = AccountRoute::class, to = TodayRoute::class, topLevel = topLevelRoutes))
    }

    @Test fun unknownDestinationsUseTheAxis() =
        assertEquals(MotionKind.AXIS_X, motionKind(from = null, to = TasksRoute::class, topLevel = topLevelRoutes))

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
