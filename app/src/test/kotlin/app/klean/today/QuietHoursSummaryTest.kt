package app.klean.today

import app.klean.shared.time.QuietHours
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursSummaryTest {
    private val dubai = TimeZone.of("Asia/Dubai")
    private fun at(h: Int, m: Int = 0) = LocalDateTime(2026, 11, 11, h, m).toInstant(dubai).toEpochMilliseconds()

    @Test fun insideQuietHoursShowsOneTileUntilTheEnd() {
        val tiles = quietTile(QuietHours.DEFAULT, at(2, 30), dubai, "Quiet hours") { "until $it" }
        assertEquals(listOf(QuietHoursSummary.KEY), tiles.map { it.key })
        assertEquals("Quiet hours", tiles.single().label)
        assertEquals("until ${at(8)}", tiles.single().value)
    }

    @Test fun outsideQuietHoursShowsNothing() {
        assertTrue(quietTile(QuietHours.DEFAULT, at(8), dubai, "Quiet hours") { "until $it" }.isEmpty())
        assertTrue(quietTile(QuietHours.DEFAULT, at(14), dubai, "Quiet hours") { "until $it" }.isEmpty())
    }

    @Test fun windowAcrossMidnight() {
        val q = QuietHours(23 * 60, 7 * 60)
        assertEquals("until ${at(7)}", quietTile(q, at(1), dubai, "Quiet hours") { "until $it" }.single().value)
        assertTrue(quietTile(q, at(22, 59), dubai, "Quiet hours") { "until $it" }.isEmpty())
    }
}
