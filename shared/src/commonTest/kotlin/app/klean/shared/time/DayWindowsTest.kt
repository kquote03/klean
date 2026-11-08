package app.klean.shared.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DayWindowsTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private fun t(h: Int, m: Int = 0, day: Int = 8) = atMinute(LocalDate(2026, 11, day), h * 60 + m, zone)
    private val nov8 = LocalDate(2026, 11, 8)
    private val nov9 = LocalDate(2026, 11, 9)

    @Test fun midnightToEight() {
        val q = QuietHours(0, 8 * 60)
        assertEquals(240, DayWindows.boundaryShiftMinutes(q))
        assertEquals(nov8, DayWindows.logicalDate(t(2, day = 9), q, zone))
        val w = DayWindows.forDate(nov8, q, zone)
        assertEquals(nov8, w.date)
        assertEquals(t(4), w.start)
        assertEquals(t(4, day = 9), w.end)
        assertEquals(t(8), w.wakeStart)
        assertEquals(t(0, day = 9), w.wakeEnd)
    }

    @Test fun elevenToSeven() {
        val q = QuietHours(23 * 60, 7 * 60)
        assertEquals(180, DayWindows.boundaryShiftMinutes(q))
        val w = DayWindows.forDate(nov8, q, zone)
        assertEquals(t(7), w.wakeStart)
        assertEquals(t(23), w.wakeEnd)
    }

    @Test fun eightPmToTwo_negativeShift() {
        val q = QuietHours(20 * 60, 2 * 60)
        assertEquals(-60, DayWindows.boundaryShiftMinutes(q))
        assertEquals(nov9, DayWindows.logicalDate(t(23, 30), q, zone))
        val w = DayWindows.forDate(nov8, q, zone)
        assertEquals(t(23, day = 7), w.start)
        assertEquals(t(2), w.wakeStart)
        assertEquals(t(20), w.wakeEnd)
    }

    @Test fun disabled_wholeDay() {
        val q = QuietHours(0, 0)
        assertEquals(0, DayWindows.boundaryShiftMinutes(q))
        val w = DayWindows.forDate(nov8, q, zone)
        assertEquals(t(0), w.start)
        assertEquals(t(0, day = 9), w.end)
        assertEquals(w.start, w.wakeStart)
        assertEquals(w.end, w.wakeEnd)
        assertEquals(nov8, DayWindows.logicalDate(t(23, 59), q, zone))
    }
}
