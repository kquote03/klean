package app.klean.feature.reminders.ui

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatsTest {
    private val wed = LocalDate(2026, 11, 11)

    @Test fun presetsRoundTrip() {
        listOf(
            RepeatChoice(RepeatKind.DAILY), RepeatChoice(RepeatKind.WEEKDAYS), RepeatChoice(RepeatKind.WEEKLY),
            RepeatChoice(RepeatKind.MONTHLY), RepeatChoice(RepeatKind.YEARLY),
            RepeatChoice(RepeatKind.EVERY_N_DAYS, 3), RepeatChoice(RepeatKind.EVERY_N_WEEKS, 2),
        ).forEach { c -> assertEquals(c, Repeats.choiceOf(Repeats.ruleFor(c, wed))) }
    }

    @Test fun anchorsOnTheDate() {
        assertEquals("FREQ=WEEKLY;BYDAY=WE", Repeats.ruleFor(RepeatChoice(RepeatKind.WEEKLY), wed))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE", Repeats.ruleFor(RepeatChoice(RepeatKind.EVERY_N_WEEKS, 2), wed))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=11", Repeats.ruleFor(RepeatChoice(RepeatKind.MONTHLY), wed))
    }

    @Test fun anythingElseIsCustom() {
        assertEquals(RepeatChoice(RepeatKind.CUSTOM, custom = "FREQ=DAILY;COUNT=5"), Repeats.choiceOf("FREQ=DAILY;COUNT=5"))
        assertEquals(RepeatKind.CUSTOM, Repeats.choiceOf("FREQ=MONTHLY;BYDAY=-1FR").kind)
        assertEquals(RepeatKind.CUSTOM, Repeats.choiceOf("FREQ=WEEKLY;BYDAY=MO,WE").kind)
        assertEquals(RepeatChoice(RepeatKind.NONE), Repeats.choiceOf(null))
    }

    @Test fun customValidation() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", Repeats.ruleFor(RepeatChoice(RepeatKind.CUSTOM, custom = "freq=weekly;byday=we,mo"), wed))
        assertNull(Repeats.ruleFor(RepeatChoice(RepeatKind.CUSTOM, custom = "FREQ=HOURLY"), wed))
        assertTrue(Repeats.isValidCustom("RRULE:FREQ=YEARLY"))
        assertFalse(Repeats.isValidCustom("every other tuesday"))
    }
}
