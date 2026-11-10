package app.klean.shared.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecurrenceRuleTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun rule(text: String) = assertNotNull(RecurrenceRule.parse(text), "parses \"$text\"")

    /** 2026-11-09 is a Monday. */
    private val monday = d(2026, 11, 9)

    // Plan S1.1.
    @Test fun everyWeekdayFromMonday() {
        val r = rule("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR")
        val got = generateSequence(monday) { r.next(it) }.take(7).toList()
        assertEquals(
            listOf(d(2026, 11, 9), d(2026, 11, 10), d(2026, 11, 11), d(2026, 11, 12), d(2026, 11, 13), d(2026, 11, 16), d(2026, 11, 17)),
            got,
        )
    }

    @Test fun monthlyOnJan31ClampsToFeb28ThenReturnsTo31() {
        val r = RecurrenceRule.monthly(d(2026, 1, 31))
        assertEquals(d(2026, 2, 28), r.next(d(2026, 1, 31)))
        assertEquals(d(2026, 3, 31), r.next(d(2026, 2, 28)))
        assertEquals(d(2026, 4, 30), r.next(d(2026, 3, 31)))
    }

    @Test fun plainMonthlyWithoutMonthDayClampsFromStart() {
        val r = rule("FREQ=MONTHLY")
        assertEquals(d(2026, 2, 28), r.next(d(2026, 1, 31)))
        // Anchored at the series start, so the 31st comes back.
        assertEquals(d(2026, 3, 31), r.next(after = d(2026, 2, 28), start = d(2026, 1, 31)))
    }

    @Test fun everyOtherWeek() {
        val r = rule("FREQ=WEEKLY;INTERVAL=2")
        assertEquals(d(2026, 11, 23), r.next(monday))
        assertEquals(d(2026, 12, 7), r.next(d(2026, 11, 23)))
    }

    @Test fun everyOtherWeekOnTwoDaysStaysInStartWeek() {
        val r = rule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH")
        assertEquals(
            listOf(d(2026, 11, 9), d(2026, 11, 12), d(2026, 11, 23), d(2026, 11, 26), d(2026, 12, 7)),
            r.occurrences(start = monday, from = monday, to = d(2026, 12, 7)),
        )
    }

    @Test fun countIsExhausted() {
        val r = rule("FREQ=DAILY;COUNT=3")
        assertEquals(d(2026, 11, 10), r.next(after = d(2026, 11, 9), start = monday))
        assertEquals(d(2026, 11, 11), r.next(after = d(2026, 11, 10), start = monday))
        assertNull(r.next(after = d(2026, 11, 11), start = monday))
    }

    @Test fun countWithoutStartCountsTheGivenDateAsTheFirst() {
        assertNull(rule("FREQ=DAILY;COUNT=1").next(monday))
        assertEquals(d(2026, 11, 10), rule("FREQ=DAILY;COUNT=2").next(monday))
    }

    @Test fun remainingCountsDown() {
        val r = rule("FREQ=DAILY;COUNT=3")
        assertEquals("FREQ=DAILY;COUNT=2", r.remainingAfterOne()?.format())
        assertNull(rule("FREQ=DAILY;COUNT=1").remainingAfterOne())
        assertEquals("FREQ=DAILY", rule("FREQ=DAILY").remainingAfterOne()?.format())
    }

    @Test fun untilIsInclusive() {
        val r = rule("FREQ=DAILY;UNTIL=20261111")
        assertEquals(d(2026, 11, 11), r.next(d(2026, 11, 10)))
        assertNull(r.next(d(2026, 11, 11)))
    }

    @Test fun untilAsUtcDateTime() {
        val r = rule("FREQ=WEEKLY;UNTIL=20261130T235959Z")
        assertEquals(d(2026, 11, 30), r.until)
    }

    // Amendments: every N days / weeks.
    @Test fun everyThreeDays() {
        val r = RecurrenceRule.everyNDays(3)
        assertEquals("FREQ=DAILY;INTERVAL=3", r.format())
        assertEquals(d(2026, 11, 12), r.next(monday))
    }

    @Test fun everyThreeWeeksOnWednesday() {
        val r = RecurrenceRule.everyNWeeks(3, setOf(DayOfWeek.WEDNESDAY))
        assertEquals("FREQ=WEEKLY;INTERVAL=3;BYDAY=WE", r.format())
        assertEquals(d(2026, 11, 11), r.next(monday))
        assertEquals(d(2026, 12, 2), r.next(d(2026, 11, 11)))
    }

    @Test fun yearlyOnFeb29ClampsInCommonYears() {
        val r = RecurrenceRule.yearly(d(2028, 2, 29))
        assertEquals(d(2029, 2, 28), r.next(d(2028, 2, 29)))
        assertEquals(d(2032, 2, 29), r.next(after = d(2031, 2, 28), start = d(2028, 2, 29)))
    }

    @Test fun yearlyByMonthAndDay() {
        val r = rule("FREQ=YEARLY;BYMONTH=3;BYMONTHDAY=3")
        assertEquals(d(2027, 3, 3), r.next(monday))
    }

    @Test fun monthlyNthWeekday() {
        val r = rule("FREQ=MONTHLY;BYDAY=2MO")
        assertEquals(d(2026, 12, 14), r.next(monday))
        assertEquals(d(2026, 11, 30), rule("FREQ=MONTHLY;BYDAY=-1MO").next(monday))
    }

    @Test fun monthlyLastDay() {
        val r = rule("FREQ=MONTHLY;BYMONTHDAY=-1")
        assertEquals(d(2026, 11, 30), r.next(monday))
        assertEquals(d(2027, 2, 28), r.next(d(2027, 1, 31)))
    }

    @Test fun dailyOnWeekdaysFilters() {
        val r = rule("FREQ=DAILY;BYDAY=SA,SU")
        assertEquals(d(2026, 11, 14), r.next(monday))
    }

    @Test fun occurrencesInRange() {
        val r = rule("FREQ=WEEKLY;BYDAY=MO")
        assertEquals(
            listOf(d(2026, 11, 16), d(2026, 11, 23)),
            r.occurrences(start = d(2026, 1, 5), from = d(2026, 11, 10), to = d(2026, 11, 23)),
        )
    }

    @Test fun occurrencesRespectCountFromStart() {
        val r = rule("FREQ=DAILY;COUNT=5")
        assertEquals(
            listOf(d(2026, 11, 12), d(2026, 11, 13)),
            r.occurrences(start = monday, from = d(2026, 11, 12), to = d(2026, 12, 31)),
        )
    }

    // Parsing and formatting.
    @Test fun parsesRrulePrefixLowercaseAndOrder() {
        val r = rule("rrule:byday=fr,mo;interval=2;freq=weekly")
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR", r.format())
    }

    @Test fun roundTrips() {
        listOf(
            "FREQ=DAILY",
            "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            "FREQ=MONTHLY;BYMONTHDAY=31",
            "FREQ=MONTHLY;BYDAY=-1FR",
            "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29",
            "FREQ=DAILY;INTERVAL=2;COUNT=10",
            "FREQ=WEEKLY;UNTIL=20270101",
        ).forEach { assertEquals(it, rule(it).format()) }
    }

    @Test fun ignoresWkstAndDefaultInterval() {
        assertEquals("FREQ=WEEKLY", rule("FREQ=WEEKLY;WKST=MO;INTERVAL=1").format())
    }

    @Test fun rejectsWhatItCannotFollow() {
        listOf(
            "", "FREQ=HOURLY", "FREQ=SECONDLY", "INTERVAL=2", "FREQ=WEEKLY;INTERVAL=0", "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=MONTHLY;BYSETPOS=1;BYDAY=MO", "FREQ=DAILY;COUNT=2;UNTIL=20270101", "FREQ=WEEKLY;BYDAY=2MO",
            "FREQ=DAILY;COUNT=abc", "FREQ=MONTHLY;BYMONTHDAY=0", "nonsense",
        ).forEach { assertNull(RecurrenceRule.parse(it), "rejects \"$it\"") }
    }

    @Test fun presetsFromADate() {
        assertEquals("FREQ=WEEKLY;BYDAY=WE", RecurrenceRule.weekly(d(2026, 11, 11)).format())
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", RecurrenceRule.weekdays().format())
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=11", RecurrenceRule.monthly(d(2026, 11, 11)).format())
        assertEquals("FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=11", RecurrenceRule.yearly(d(2026, 11, 11)).format())
        assertEquals("FREQ=DAILY", RecurrenceRule.daily().format())
    }
}
