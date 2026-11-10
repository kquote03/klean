package app.klean.shared.health

import kotlin.test.Test
import kotlin.test.assertEquals

class HealthMergeTest {
    private val h = 3_600_000L

    @Test fun overlapIsMeasuredAgainstTheShorterSpan() {
        assertEquals(1.0, HealthMerge.overlap(Span(0, 8 * h), Span(1 * h, 3 * h)))
        assertEquals(0.5, HealthMerge.overlap(Span(0, 4 * h), Span(2 * h, 6 * h)))
        assertEquals(0.0, HealthMerge.overlap(Span(0, 2 * h), Span(3 * h, 4 * h)))
        assertEquals(0.0, HealthMerge.overlap(Span(0, 0), Span(0, 4 * h)))
    }

    @Test fun sleepManualNightOverlappingHealthConnectByMoreThanHalfIsReplaced() {
        val manual = listOf(
            "m1" to Span(0, 8 * h),              // same night as hc1: replaced
            "m2" to Span(24 * h, 32 * h),        // its own night: kept
            "m3" to Span(48 * h, 52 * h),        // exactly 50% overlap: kept (above 50% only)
        )
        val hc = listOf(Span(30 * 60_000L, 8 * h), Span(50 * h, 54 * h))
        assertEquals(setOf("m1"), HealthMerge.manualSleepToReplace(manual, hc))
    }

    @Test fun workoutDuplicatesAbove70PercentKeepTheOneWithARoute() {
        val klean = WorkoutSpan("k", Span(0, 30 * 60_000L), hasRoute = true)
        val watch = WorkoutSpan("w", Span(2 * 60_000L, 31 * 60_000L), hasRoute = false)
        assertEquals(Duplicate(keep = "k", link = "w"), HealthMerge.duplicate(klean, watch))
        assertEquals(Duplicate(keep = "k", link = "w"), HealthMerge.duplicate(watch, klean))
    }

    @Test fun workoutDuplicateWithoutRoutesKeepsTheFirst() {
        val a = WorkoutSpan("a", Span(0, 30 * 60_000L), hasRoute = false)
        val b = WorkoutSpan("b", Span(0, 30 * 60_000L), hasRoute = false)
        assertEquals(Duplicate(keep = "a", link = "b"), HealthMerge.duplicate(a, b))
    }

    @Test fun workoutsOverlappingLessThan70PercentAreBothKept() {
        val a = WorkoutSpan("a", Span(0, 30 * 60_000L), hasRoute = true)
        val b = WorkoutSpan("b", Span(15 * 60_000L, 45 * 60_000L), hasRoute = false)
        assertEquals(null, HealthMerge.duplicate(a, b))
    }
}
