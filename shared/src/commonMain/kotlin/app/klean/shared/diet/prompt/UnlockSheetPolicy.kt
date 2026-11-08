package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.QuietHours
import kotlinx.datetime.TimeZone

/** Decides whether the gentle unlock sheet may appear. Pure, so it can be tested without Android. */
object UnlockSheetPolicy {
    const val MIN_SCREEN_OFF_MS = 3 * HOUR_MS
    const val MIN_BETWEEN_SHOWS_MS = 3 * HOUR_MS

    fun shouldShow(
        now: Long,
        lastScreenOffAt: Long?,
        lastShownAt: Long?,
        quiet: QuietHours,
        zone: TimeZone,
        enabled: Boolean,
    ): Boolean = enabled &&
        lastScreenOffAt != null &&
        now - lastScreenOffAt >= MIN_SCREEN_OFF_MS &&
        !quiet.contains(now, zone) &&
        (lastShownAt == null || now - lastShownAt >= MIN_BETWEEN_SHOWS_MS)
}
