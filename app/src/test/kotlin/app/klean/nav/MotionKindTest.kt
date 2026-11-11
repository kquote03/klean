package app.klean.nav

import app.klean.feature.tasks.TaskEditRoute
import app.klean.feature.tasks.TasksRoute
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion by relationship (plan 2026-11-11, E3): a page slide between top-level destinations, shared axis X into a
 * detail and back, slide up for full-screen pages.
 */
class MotionKindTest {
    private val topLevelRoutes: Set<KClass<*>> = setOf(TodayRoute::class, TrackWeekRoute::class, TasksRoute::class)

    @Test fun groupSwitchIsAPageSlide() =
        assertEquals(MotionKind.PAGE, motionKind(from = TodayRoute::class, to = TasksRoute::class, topLevel = topLevelRoutes))

    @Test fun backToTodayIsAPageSlideToo() =
        assertEquals(MotionKind.PAGE, motionKind(from = TasksRoute::class, to = TodayRoute::class, topLevel = topLevelRoutes))

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

    /** A page slide has no zoom: the old page leaves the way the new one pushes it, full width. */
    @Test fun aPageSlidesTheOtherWayBack() {
        val m = KleanMotion(reduced = false)
        org.junit.Assert.assertNotEquals(m.enter(MotionKind.PAGE, forward = true), m.enter(MotionKind.PAGE, forward = false))
        org.junit.Assert.assertNotEquals(m.exit(MotionKind.PAGE, forward = true), m.exit(MotionKind.PAGE, forward = false))
    }

    /** Direction comes from the page order, for pops too: Back from Track to Today brings Today in from the left. */
    @Test fun pageDirectionFollowsThePageOrder() {
        val order: List<KClass<*>> = listOf(TodayRoute::class, TasksRoute::class, TrackWeekRoute::class)
        assertEquals(false, motionForward(order, from = TrackWeekRoute::class, to = TodayRoute::class, kind = MotionKind.PAGE, push = false))
        assertEquals(false, motionForward(order, from = TrackWeekRoute::class, to = TodayRoute::class, kind = MotionKind.PAGE, push = true))
        assertEquals(true, motionForward(order, from = TodayRoute::class, to = TrackWeekRoute::class, kind = MotionKind.PAGE, push = false))
        // Detail screens keep push/pop: in on a push, back out on a pop.
        assertEquals(true, motionForward(order, from = TasksRoute::class, to = TaskEditRoute::class, kind = MotionKind.AXIS_X, push = true))
        assertEquals(false, motionForward(order, from = TaskEditRoute::class, to = TasksRoute::class, kind = MotionKind.AXIS_X, push = false))
    }
}
