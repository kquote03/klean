package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptPlannerTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private val quiet = QuietHours(0, 8 * 60)
    private val lunchSlot = listOf(MealSlot(13 * 60, 13 * 60 + 45, MealLabel.LUNCH))
    private fun t(h: Int, m: Int = 0, day: Int = 9) = atMinute(LocalDate(2026, 11, day), h * 60 + m, zone)
    private fun plan(now: Long, lastPrompt: Long? = null, lastEntry: Long? = null, slots: List<MealSlot> = emptyList()) =
        PromptPlanner.next(PromptContext(now, zone, quiet, lastPrompt, lastEntry, slots))

    @Test fun noHistory_sixHoursFromNow() = assertEquals(t(16), plan(now = t(10)).at)
    @Test fun anchorsOnLatestOfPromptAndEntry() = assertEquals(t(17), plan(now = t(12), lastPrompt = t(9), lastEntry = t(11)).at)
    @Test fun baseInQuiet_movesToWakePlusHour() {
        val p = plan(now = t(20), lastEntry = t(20)); assertEquals(t(9, day = 10), p.at); assertEquals(PromptTrigger.CADENCE, p.trigger)
    }
    @Test fun neverInsideQuietHours() {
        for (m in 0 until 1440 step 7) { val now = t(0) + m * MINUTE_MS
            val p = plan(now = now, lastEntry = now); assertFalse(quiet.contains(p.at, zone)); assertTrue(p.at > now) }
    }
    @Test fun neverLaterThanCadenceOutsideQuiet() {
        for (m in 8 * 60 until 18 * 60 step 13) { val now = t(0) + m * MINUTE_MS
            val p = plan(now = now, lastPrompt = now - HOUR_MS, slots = lunchSlot); assertTrue(p.at <= now - HOUR_MS + 6 * HOUR_MS) }
    }
    @Test fun learnedSlotBeforeBase_chosen() {
        val p = plan(now = t(10), lastPrompt = t(9), lastEntry = t(8, 30), slots = lunchSlot)
        assertEquals(t(13, 45), p.at); assertEquals(PromptTrigger.LEARNED_MEAL, p.trigger); assertEquals(MealLabel.LUNCH, p.meal)
    }
    @Test fun learnedSlotTooSoonAfterLastPrompt_skipped() = assertEquals(t(18), plan(now = t(12, 5), lastPrompt = t(12), slots = lunchSlot).at)
    @Test fun learnedSlotSkippedWhenRecentlyLogged() = assertEquals(t(18, 30), plan(now = t(12, 40), lastPrompt = t(9), lastEntry = t(12, 30), slots = lunchSlot).at)
}
