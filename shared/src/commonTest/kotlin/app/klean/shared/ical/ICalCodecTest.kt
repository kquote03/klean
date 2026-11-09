package app.klean.shared.ical

import app.klean.shared.vcard.ContentLine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ICalCodecTest {
    private val dubai = TimeZone.of("Asia/Dubai")
    private val start = LocalDateTime(2026, 11, 10, 14, 0).toInstant(dubai).toEpochMilliseconds()

    private val dentist = ICalEvent(
        uid = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
        summary = "Dentist, Dr. Haddad",
        description = "Bring the form.\nAsk about the X-ray.",
        location = "Clinic; Floor 2",
        start = ICalTime.utc(start),
        end = ICalTime.utc(start + 3_600_000),
        rrule = "FREQ=MONTHLY;COUNT=3",
        exdates = listOf(ICalTime.utc(start + 31L * 86_400_000)),
        alarmMinutesBefore = 30,
        sequence = 2,
        dtstamp = "20261109T120000Z",
        extras = listOf(ContentLine(null, "X-KLEAN-TEST", emptyList(), "kept")),
    )

    @Test fun roundTripsATimedEvent() {
        val text = ICalCodec.write(listOf(dentist))
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:"), text)
        assertTrue("DTSTART:20261110T100000Z\r\n" in text, text)
        assertTrue("TRIGGER:-PT30M\r\n" in text, text)
        assertTrue("SUMMARY:Dentist\\, Dr. Haddad\r\n" in text, text)
        assertEquals(listOf(dentist), ICalCodec.parse(text))
    }

    @Test fun roundTripsAnAllDayEvent() {
        val day = ICalEvent(
            uid = "bday-1", summary = "Amira's birthday",
            start = ICalTime.date(LocalDate(2026, 4, 12)), end = ICalTime.date(LocalDate(2026, 4, 13)),
            rrule = "FREQ=YEARLY", dtstamp = "20261109T120000Z",
        )
        val text = ICalCodec.write(listOf(day))
        assertTrue("DTSTART;VALUE=DATE:20260412\r\n" in text, text)
        val back = ICalCodec.parse(text).single()
        assertEquals(day, back)
        assertTrue(back.allDay)
        assertEquals(LocalDate(2026, 4, 12).atStartOfDayIn(dubai).toEpochMilliseconds(), back.start.toEpochMs(dubai))
    }

    @Test fun parsesTzidFloatingDurationAndMultipleExdates() {
        val text = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example//EN
            BEGIN:VTIMEZONE
            TZID:Asia/Dubai
            BEGIN:STANDARD
            DTSTART:19700101T000000
            TZOFFSETFROM:+0400
            TZOFFSETTO:+0400
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:weekly@example
            DTSTAMP:20261101T000000Z
            DTSTART;TZID=Asia/Dubai:20261110T140000
            DURATION:PT45M
            RRULE:FREQ=WEEKLY;BYDAY=TU
            EXDATE;TZID=Asia/Dubai:20261117T140000,20261124T140000
            EXDATE;TZID=Asia/Dubai:20261201T140000
            SUMMARY:Standup
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER;RELATED=START:-PT1H30M
            END:VALARM
            END:VEVENT
            BEGIN:VTODO
            UID:ignored
            END:VTODO
            END:VCALENDAR
        """.trimIndent()
        val e = ICalCodec.parse(text).single()
        assertEquals("Asia/Dubai", e.start.tzid)
        assertEquals(start, e.start.toEpochMs(TimeZone.UTC))
        assertEquals(start + 45 * 60_000, e.endEpochMs(TimeZone.UTC))
        assertEquals(3, e.exdates.size)
        assertEquals(90, e.alarmMinutesBefore)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", e.rrule)

        val floating = ICalCodec.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:f\nDTSTART:20261110T140000\nEND:VEVENT\nEND:VCALENDAR\n").single()
        assertEquals(start, floating.start.toEpochMs(dubai))
        assertNull(floating.end)
        assertEquals(start, floating.endEpochMs(dubai))
    }

    @Test fun durationsParse() {
        assertEquals(-30, ICalCodec.durationMinutes("-PT30M"))
        assertEquals(-1440, ICalCodec.durationMinutes("-P1D"))
        assertEquals(90, ICalCodec.durationMinutes("PT1H30M"))
        assertEquals(-10080, ICalCodec.durationMinutes("-P1W"))
        assertNull(ICalCodec.durationMinutes("soon"))
    }
}
