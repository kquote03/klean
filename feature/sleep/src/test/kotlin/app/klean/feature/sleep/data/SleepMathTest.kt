package app.klean.feature.sleep.data

import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.feature.sleep.Dubai
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepMathTest {
    private val nov8 = LocalDate(2026, 11, 8)
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private val q = QuietHours(23 * 60, 7 * 60)
    private val sync = SyncColumns(0, 0, "d", false, 0)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private fun s(id: String, bed: Long, wake: Long?) = SleepSessionEntity(id, bed, wake, sync = sync)

    @Test fun durationAndParts() {
        assertEquals(7 * 3_600_000L + 20 * 60_000L, SleepMath.duration(s("a", at(nov9, 23, 40), at(nov10, 7))))
        assertNull(SleepMath.duration(s("a", at(nov9, 23), null)))
        assertEquals(7 to 20, SleepMath.hoursMinutes(7 * 3_600_000L + 20 * 60_000L + 59_000L))
    }

    @Test fun lastNightIsTheLatestSessionWakingInTheLogicalDay() {
        val sessions = listOf(
            s("nap", at(nov10, 14), at(nov10, 14, 30)),
            s("night", at(nov9, 23), at(nov10, 7)),
            s("before", at(nov8, 23), at(nov9, 7)),
        )
        // At 08:00 the nap hasn't happened; at 16:00 it's the latest.
        assertEquals("night", SleepMath.lastNight(sessions.drop(1), nov10, q, Dubai)?.id)
        assertEquals("nap", SleepMath.lastNight(sessions, nov10, q, Dubai)?.id)
        assertEquals("before", SleepMath.lastNight(sessions, nov9, q, Dubai)?.id)
        // An open session has no wake yet.
        assertNull(SleepMath.lastNight(listOf(s("open", at(nov9, 23), null)), nov10, q, Dubai))
    }

    @Test fun nightsAreOnePerWakeDayNewestFirstWithBlanks() {
        val sessions = listOf(s("n10", at(nov9, 23), at(nov10, 7)), s("n8", at(nov7(), 23, 30), at(nov8, 6)))
        val nights = SleepMath.nights(sessions, nov10, 3, q, Dubai)
        assertEquals(listOf(nov10, nov9, nov8), nights.map { it.date })
        assertEquals(listOf(8 * 60, null, 6 * 60 + 30), nights.map { n -> n.durationMs?.let { (it / 60_000).toInt() } })
    }

    private fun nov7() = LocalDate(2026, 11, 7)
}
