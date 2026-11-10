package app.klean.feature.sleep.notify

import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.SleepOffer
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BedtimePolicyTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)

    @Test fun thirtyMinutesBeforeBedtimeNextOccurrence() {
        assertEquals(at(nov9, 22, 30), BedtimePolicy.nextAt(at(nov9, 12), 23 * 60, 30, Dubai))
        assertEquals(at(nov10, 22, 30), BedtimePolicy.nextAt(at(nov9, 22, 30), 23 * 60, 30, Dubai))
        // Midnight bedtime: 23:30 the evening before.
        assertEquals(at(nov9, 23, 30), BedtimePolicy.nextAt(at(nov9, 12), 0, 30, Dubai))
    }

    @Test fun goingToBedIsOfferedAroundBedtimeOnly() {
        val q = QuietHours(23 * 60, 7 * 60)
        assertNull(SleepOffer.offerBedtime(at(nov9, 12), q, Dubai))
        assertEquals(at(nov9, 23), SleepOffer.offerBedtime(at(nov9, 21, 30), q, Dubai))
        assertEquals(at(nov9, 23), SleepOffer.offerBedtime(at(nov10, 3), q, Dubai))
        assertNull(SleepOffer.offerBedtime(at(nov10, 7, 30), q, Dubai))
        // A midnight bedtime is offered from 22:00 the day before.
        assertEquals(at(nov10, 0), SleepOffer.offerBedtime(at(nov9, 22, 30), QuietHours(0, 8 * 60), Dubai))
        assertNull(SleepOffer.offerBedtime(at(nov9, 22, 30), QuietHours(0, 0), Dubai))
    }
}
