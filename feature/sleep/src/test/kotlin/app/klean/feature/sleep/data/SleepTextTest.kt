package app.klean.feature.sleep.data

import app.klean.feature.sleep.Dubai
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTextTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private val q = QuietHours(23 * 60, 7 * 60)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private fun parse(text: String, now: Long) = SleepText.parse(text, now, Dubai, q)

    @Test fun bedAndWakeAcrossMidnightInDubai() {
        val now = at(nov10, 8)
        assertEquals(SleepCommand.Night(at(nov9, 23), at(nov10, 7)), parse("slept at 11 woke at 7", now))
        assertEquals(SleepCommand.Night(at(nov9, 23, 30), at(nov10, 6)), parse("slept from 23:30 to 6", now))
        assertEquals(SleepCommand.Night(at(nov10, 1), at(nov10, 9)), parse("slept 1-9", at(nov10, 10)))
        assertEquals(SleepCommand.Night(at(nov9, 22, 45), at(nov10, 6, 15)), parse("went to bed at 10:45pm, woke up at 6:15am", now))
    }

    @Test fun oneSidedPhrases() {
        val late = at(nov9, 23, 40)
        assertEquals(SleepCommand.GoToBed(late), parse("going to bed", late))
        assertEquals(SleepCommand.GoToBed(late), parse("Bedtime", late))
        assertEquals(SleepCommand.GoToBed(at(nov9, 22, 30)), parse("went to bed at 10:30", late))
        assertEquals(SleepCommand.WakeUp(at(nov10, 6, 45)), parse("woke up at 6:45", at(nov10, 8)))
        assertEquals(SleepCommand.WakeUp(at(nov10, 8)), parse("woke up", at(nov10, 8)))
        assertEquals(SleepCommand.WakeUp(at(nov10, 8)), parse("I'm up", at(nov10, 8)))
    }

    @Test fun aDurationEndsAtTheScheduledWakeOrNow() {
        assertEquals(SleepCommand.Night(at(nov10, 0), at(nov10, 7)), parse("slept 7 hours", at(nov10, 9)))
        assertEquals(SleepCommand.Night(at(nov10, 0), at(nov10, 6, 30)), parse("slept 6.5 h", at(nov10, 6, 30)))
    }
}
