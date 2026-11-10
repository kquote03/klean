package app.klean.feature.calendar.data

import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.Dubai
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.dubai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceExpanderTest {
    // Monday 2 Nov 2026, 09:00 Dubai.
    private val mon = dubai(2026, 11, 2, 9)

    private fun expand(
        rrule: String?,
        from: Long,
        to: Long,
        start: Long = mon,
        duration: Long = HOUR,
        exdates: Set<Long> = emptySet(),
    ) = RecurrenceExpander.occurrences(start, duration, rrule, exdates, Dubai, from, to)

    @Test fun aOneOffEventAppearsOnlyWhenItOverlapsTheRange() {
        assertEquals(listOf(mon), expand(null, mon - DAY, mon + DAY))
        // Started before the range and still running: included.
        assertEquals(listOf(mon), expand(null, mon + HOUR / 2, mon + DAY))
        assertEquals(emptyList<Long>(), expand(null, mon + HOUR, mon + DAY))
        assertEquals(emptyList<Long>(), expand(null, mon - DAY, mon))
    }

    @Test fun dailyWithIntervalAndCount() {
        assertEquals(listOf(mon, mon + 2 * DAY, mon + 4 * DAY), expand("FREQ=DAILY;INTERVAL=2;COUNT=3", mon, mon + 30 * DAY))
    }

    @Test fun weeklyOnSeveralWeekdays() {
        val got = expand("FREQ=WEEKLY;BYDAY=MO,WE,FR", mon, mon + 14 * DAY)
        val days = listOf(0, 2, 4, 7, 9, 11).map { mon + it * DAY }
        assertEquals(days, got)
    }

    @Test fun weeklyByDayStartingMidWeekSkipsEarlierDaysOfTheFirstWeek() {
        val wed = mon + 2 * DAY
        assertEquals(listOf(wed, mon + 7 * DAY, wed + 7 * DAY), expand("FREQ=WEEKLY;BYDAY=MO,WE", wed, wed + 8 * DAY, start = wed))
    }

    @Test fun everyOtherWeek() {
        assertEquals(listOf(mon, mon + 14 * DAY, mon + 28 * DAY), expand("FREQ=WEEKLY;INTERVAL=2", mon, mon + 35 * DAY))
    }

    @Test fun monthlyOnThe31stSkipsShortMonths() {
        val jan31 = dubai(2027, 1, 31, 9)
        val got = expand("FREQ=MONTHLY;COUNT=3", jan31, dubai(2028, 1, 1), start = jan31)
        assertEquals(listOf(jan31, dubai(2027, 3, 31, 9), dubai(2027, 5, 31, 9)), got)
    }

    @Test fun monthlyOnTheSecondTuesday() {
        val tue = dubai(2026, 11, 10, 18)
        val got = expand("FREQ=MONTHLY;BYDAY=2TU", tue, dubai(2027, 2, 1), start = tue)
        assertEquals(listOf(tue, dubai(2026, 12, 8, 18), dubai(2027, 1, 12, 18)), got)
    }

    @Test fun monthlyOnTheLastFriday() {
        val fri = dubai(2026, 11, 27, 18)
        val got = expand("FREQ=MONTHLY;BYDAY=-1FR", fri, dubai(2027, 1, 31), start = fri)
        assertEquals(listOf(fri, dubai(2026, 12, 25, 18), dubai(2027, 1, 29, 18)), got)
    }

    @Test fun yearlyOnFebruary29OnlyInLeapYears() {
        val leap = dubai(2028, 2, 29)
        val got = expand("FREQ=YEARLY", leap, dubai(2037, 1, 1), start = leap, duration = DAY)
        assertEquals(listOf(leap, dubai(2032, 2, 29), dubai(2036, 2, 29)), got)
    }

    @Test fun untilIsInclusive() {
        assertEquals(
            listOf(mon, mon + DAY, mon + 2 * DAY),
            expand("FREQ=DAILY;UNTIL=20261104T050000Z", mon, mon + 30 * DAY),
        )
        // A date-only UNTIL includes that whole day.
        assertEquals(listOf(mon, mon + DAY), expand("FREQ=DAILY;UNTIL=20261103", mon, mon + 30 * DAY))
    }

    @Test fun exdatesAreSkippedButStillCountTowardsCount() {
        val got = expand("FREQ=DAILY;COUNT=3", mon, mon + 30 * DAY, exdates = setOf(mon + DAY))
        assertEquals(listOf(mon, mon + 2 * DAY), got)
    }

    @Test fun onlyOccurrencesInTheRangeComeBack() {
        val from = mon + 10 * DAY
        assertEquals(listOf(from, from + DAY), expand("FREQ=DAILY", from, from + 2 * DAY))
    }

    @Test fun anUnknownRuleFallsBackToTheFirstOccurrence() {
        assertEquals(listOf(mon), expand("FREQ=SECONDLY", mon - DAY, mon + 30 * DAY))
        assertNull(RecurrenceExpander.parse("INTERVAL=2"))
    }

    @Test fun parsesAndFormatsTheSimpleRepeats() {
        assertEquals(Repeat.WEEKLY, Repeat.of("FREQ=WEEKLY"))
        assertEquals(Repeat.DAILY, Repeat.of("FREQ=DAILY"))
        assertEquals(Repeat.NONE, Repeat.of(null))
        assertEquals(Repeat.CUSTOM, Repeat.of("FREQ=WEEKLY;BYDAY=MO,WE"))
        assertEquals("FREQ=YEARLY", Repeat.YEARLY.rrule)
    }
}
