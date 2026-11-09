package app.klean.shared.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaturalTimeTest {
    /** A Monday. */
    private val now = LocalDateTime(2026, 11, 9, 10, 0)

    private fun check(input: String, date: LocalDate?, minutes: Int?, rest: String, at: LocalDateTime = now) {
        val p = NaturalTime.parse(input, at)
        assertEquals(ParsedTime(date, minutes, rest), p, "for \"$input\"")
    }

    // The plan's table.
    @Test fun atFiveIsAfternoon() = check("remind me at 5 to call Ahmed", LocalDate(2026, 11, 9), 1020, "remind me to call Ahmed")
    @Test fun tomorrowSixPm() = check("gym tomorrow 6pm", LocalDate(2026, 11, 10), 1080, "gym")
    @Test fun monthDayAlreadyPassedRollsToNextYear() =
        check("Sara's birthday is March 3", LocalDate(2027, 3, 3), null, "Sara's birthday is")
    @Test fun weekdayWithTime() = check("dentist friday 9:30", LocalDate(2026, 11, 13), 570, "dentist")
    @Test fun inTwoHours() = check("in 2 hours stretch", LocalDate(2026, 11, 9), 720, "stretch")
    @Test fun noTime() = check("pay rent", null, null, "pay rent")
    @Test fun atNinePicksNextFutureOccurrence() = check("call mum at 9", LocalDate(2026, 11, 9), 1260, "call mum")
    @Test fun dayMonthSlashAnd24h() = check("meeting 3/12 14:00", LocalDate(2026, 12, 3), 840, "meeting")

    // Edge cases.
    @Test fun atNineLateEveningIsTomorrowMorning() =
        check("call mum at 9", LocalDate(2026, 11, 10), 540, "call mum", at = LocalDateTime(2026, 11, 9, 22, 0))
    @Test fun atFiveAfterFivePmIsTomorrow() =
        check("call at 5", LocalDate(2026, 11, 10), 1020, "call", at = LocalDateTime(2026, 11, 9, 18, 0))
    @Test fun atElevenBeforeElevenIsMorning() = check("standup at 11", LocalDate(2026, 11, 9), 660, "standup")
    @Test fun explicitAmPm() {
        check("at 5am run", LocalDate(2026, 11, 10), 300, "run")
        check("lunch 12pm", LocalDate(2026, 11, 9), 720, "lunch")
        check("x at 12am", LocalDate(2026, 11, 10), 0, "x")
        check("leave 6:45 pm", LocalDate(2026, 11, 9), 1125, "leave")
    }
    @Test fun twentyFourHourPastTimeRollsToTomorrow() = check("bins 07:30", LocalDate(2026, 11, 10), 450, "bins")
    @Test fun todayKeepsDate() = check("today pay rent", LocalDate(2026, 11, 9), null, "pay rent")
    @Test fun tonightDefaultsToEight() = check("movie tonight", LocalDate(2026, 11, 9), 1200, "movie")
    @Test fun tonightAtNineIsPm() = check("movie tonight at 9", LocalDate(2026, 11, 9), 1260, "movie")
    @Test fun tomorrowAtNineIsLiteral() = check("tomorrow at 9 dentist", LocalDate(2026, 11, 10), 540, "dentist")
    @Test fun nextWeek() = check("clean car next week", LocalDate(2026, 11, 16), null, "clean car")
    @Test fun weekdayCaseInsensitiveWithOn() = check("Pay rent on Friday", LocalDate(2026, 11, 13), null, "Pay rent")
    @Test fun sameWeekdayWithoutTimeIsNextWeek() = check("gym monday", LocalDate(2026, 11, 16), null, "gym")
    @Test fun sameWeekdayLaterTodayIsToday() = check("gym monday 6pm", LocalDate(2026, 11, 9), 1080, "gym")
    @Test fun sameWeekdayEarlierTodayIsNextWeek() = check("gym monday 8am", LocalDate(2026, 11, 16), 480, "gym")
    @Test fun inThirtyMin() = check("in 30 min tea", LocalDate(2026, 11, 9), 630, "tea")
    @Test fun inHoursCrossesMidnight() =
        check("in 3 hours sleep", LocalDate(2026, 11, 10), 60, "sleep", at = LocalDateTime(2026, 11, 9, 22, 0))
    @Test fun inDaysIsDateOnly() = check("call bank in 2 days", LocalDate(2026, 11, 11), null, "call bank")
    @Test fun dayMonthWordOrder() {
        check("party 3 March", LocalDate(2027, 3, 3), null, "party")
        check("party 25th December", LocalDate(2026, 12, 25), null, "party")
        check("party on Dec 25th at 7pm", LocalDate(2026, 12, 25), 1140, "party")
    }
    @Test fun todayDateIsNotRolled() = check("ship it 9 November", LocalDate(2026, 11, 9), null, "ship it")
    @Test fun explicitYearIsKept() = check("trip 3/3/2028", LocalDate(2028, 3, 3), null, "trip")
    @Test fun invalidDateIsLeftAlone() = check("ratio 31/2", null, null, "ratio 31/2")
    @Test fun bareNumbersAreNotTimes() {
        check("did 30 pushups", null, null, "did 30 pushups")
        check("ate 2 manakish", null, null, "ate 2 manakish")
    }
    @Test fun wordsContainingMonthOrDayNamesAreKept() = check("I may eat a sundae", null, null, "I may eat a sundae")
    @Test fun noonWord() = check("lunch at noon", LocalDate(2026, 11, 9), 720, "lunch")

    @Test fun dateTimeHelper() {
        assertEquals(LocalDateTime(2026, 11, 10, 18, 0), NaturalTime.parse("gym tomorrow 6pm", now).dateTime)
        assertNull(NaturalTime.parse("gym tomorrow", now).dateTime)
    }
}
