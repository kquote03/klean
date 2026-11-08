package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockSheetPolicyTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private val quiet = QuietHours(0, 8 * 60)
    private fun t(h: Int, m: Int = 0) = atMinute(LocalDate(2026, 11, 9), h * 60 + m, zone)
    private fun show(now: Long = t(14), off: Long? = t(10), shown: Long? = null, enabled: Boolean = true) =
        UnlockSheetPolicy.shouldShow(now, off, shown, quiet, zone, enabled)

    @Test fun eligible() = assertTrue(show())
    @Test fun disabled() = assertFalse(show(enabled = false))
    @Test fun unknownScreenOff() = assertFalse(show(off = null))
    @Test fun screenOffTooShort() = assertFalse(show(off = t(14) - 3 * HOUR_MS + MINUTE_MS))
    @Test fun screenOffExactlyThreeHours() = assertTrue(show(off = t(11)))
    @Test fun insideQuietHours() = assertFalse(show(now = t(7, 30), off = t(1)))
    @Test fun shownRecently() = assertFalse(show(shown = t(12)))
    @Test fun shownLongAgo() = assertTrue(show(shown = t(11)))
}
