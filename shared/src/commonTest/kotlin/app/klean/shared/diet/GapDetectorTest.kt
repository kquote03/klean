package app.klean.shared.diet

import app.klean.shared.time.DayWindows
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class GapDetectorTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private val window = DayWindows.forDate(LocalDate(2026, 11, 9), QuietHours(0, 8 * 60), zone)
    private val threshold = 4 * HOUR_MS
    private fun t(h: Int, m: Int = 0, day: Int = 9) = atMinute(LocalDate(2026, 11, day), h * 60 + m, zone)
    private fun find(entries: List<Long>, now: Long) = GapDetector.find(entries, window, now, threshold)

    @Test fun oneClosedGap() = assertEquals(
        listOf(Gap(t(10, 30), t(17), ongoing = false)),
        find(listOf(t(9), t(10, 30), t(17)), now = t(20)),
    )
    @Test fun noEntries_ongoingGapFromWake() =
        assertEquals(listOf(Gap(t(8), t(13), ongoing = true)), find(emptyList(), now = t(13)))
    @Test fun regularEating_noGaps() =
        assertEquals(emptyList(), find(listOf(t(8, 30), t(12), t(15, 30), t(19)), now = t(21)))
    @Test fun beforeWake_empty() = assertEquals(emptyList(), find(emptyList(), now = t(7)))
    @Test fun afterWakeEnd_closedAtWakeEnd() = assertEquals(
        listOf(Gap(t(19), t(0, day = 10), ongoing = false)),
        find(listOf(t(9), t(12), t(15, 30), t(19)), now = t(2, day = 10)),
    )
    @Test fun unsortedAndOutOfWindowEntriesHandled() = assertEquals(
        listOf(Gap(t(10, 30), t(17), ongoing = false)),
        find(listOf(t(17), t(6), t(10, 30), t(9), t(23, day = 10)), now = t(20)),
    )
}
