package app.klean.feature.calendar.capture

import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.Dubai
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.dubai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureEventParserTest {
    // Tuesday 10 Nov 2026, 12:20 Dubai.
    private val now = dubai(2026, 11, 10, 12, 20)

    private fun parse(text: String, defaultReminder: Int? = 10) = CaptureEventParser.parse(text, now, Dubai, defaultReminder)

    @Test fun aDateAndTimeMakeAOneHourEvent() {
        val d = parse("dentist friday 9:30")
        assertEquals("dentist", d.title)
        assertEquals(dubai(2026, 11, 13, 9, 30), d.startAt)
        assertEquals(d.startAt + HOUR, d.endAt)
        assertFalse(d.allDay)
        assertEquals("Asia/Dubai", d.zone)
        assertEquals(10, d.reminderMinutes)
    }

    @Test fun aDateOnlyMakesAnAllDayEventWithNoReminder() {
        val d = parse("conference 3 December")
        assertEquals("conference", d.title)
        assertTrue(d.allDay)
        assertEquals(dubai(2026, 12, 3), d.startAt)
        assertEquals(dubai(2026, 12, 3) + DAY, d.endAt)
        assertNull(d.reminderMinutes)
    }

    @Test fun noTimeAtAllStartsAtTheNextWholeHour() {
        val d = parse("call with Sara")
        assertEquals("call with Sara", d.title)
        assertEquals(dubai(2026, 11, 10, 13), d.startAt)
        assertEquals(dubai(2026, 11, 10, 14), d.endAt)
    }

    @Test fun onlyTimeWordsKeepsTheWholeTextAsTheTitle() {
        assertEquals("tomorrow 6pm", parse("tomorrow 6pm").title)
    }
}
