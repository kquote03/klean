package app.klean.shared.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuietHoursTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private fun t(h: Int, m: Int = 0, day: Int = 9) = atMinute(LocalDate(2026, 11, day), h * 60 + m, zone)

    @Test fun overnight_containsNightOnly() {
        val q = QuietHours(23 * 60, 7 * 60)
        assertTrue(q.contains(t(23, 30), zone))
        assertTrue(q.contains(t(3), zone))
        assertFalse(q.contains(t(7), zone))
        assertFalse(q.contains(t(12), zone))
    }

    @Test fun sameDayRange() {
        val q = QuietHours(60, 9 * 60)
        assertTrue(q.contains(t(1), zone))
        assertFalse(q.contains(t(0, 59), zone))
    }

    @Test fun disabledContainsNothing() {
        val q = QuietHours(0, 0)
        assertTrue(q.isDisabled)
        for (m in 0 until DAY_MINUTES step 5) assertFalse(q.containsMinute(m))
        assertFalse(q.contains(t(3), zone))
    }

    @Test fun nextAllowed() {
        val q = QuietHours(23 * 60, 7 * 60)
        assertEquals(t(7, day = 10), q.nextAllowed(t(23, 30, day = 9), zone))
        assertEquals(t(7, day = 10), q.nextAllowed(t(3, day = 10), zone))
        assertEquals(t(12), q.nextAllowed(t(12), zone))
    }

    @Test fun minuteOfDayInZone() = assertEquals(13 * 60 + 5, minuteOfDay(t(13, 5), zone))
}
