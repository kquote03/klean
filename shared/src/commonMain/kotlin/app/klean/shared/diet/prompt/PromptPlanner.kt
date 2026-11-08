package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

data class PromptContext(
    val now: Long, val zone: TimeZone, val quiet: QuietHours,
    val lastPromptAt: Long?, val lastEntryAt: Long?,
    val learnedSlots: List<MealSlot> = emptyList(),
    val cadenceMs: Long = 6 * HOUR_MS,
    val minGapMs: Long = 3 * HOUR_MS,
    val recentLogGraceMs: Long = 2 * HOUR_MS,
    val wakeDelayMs: Long = HOUR_MS,
)
data class PlannedPrompt(val at: Long, val trigger: PromptTrigger, val meal: MealLabel?)

/** Pure. Deliberately takes no outcome history: ignoring prompts must never change frequency. */
object PromptPlanner {
    fun next(c: PromptContext): PlannedPrompt {
        val anchor = listOfNotNull(c.lastPromptAt, c.lastEntryAt).maxOrNull() ?: c.now
        val base = maxOf(anchor + c.cadenceMs, c.now + MINUTE_MS)
        val floor = maxOf(c.now, (c.lastPromptAt ?: (Long.MIN_VALUE / 2)) + c.minGapMs)
        learned(c, floor, base)?.let { return it }
        val allowed = c.quiet.nextAllowed(base, c.zone)
        return PlannedPrompt(if (allowed != base) allowed + c.wakeDelayMs else base, PromptTrigger.CADENCE, null)
    }
    private fun learned(c: PromptContext, floor: Long, base: Long): PlannedPrompt? {
        if (c.learnedSlots.isEmpty()) return null
        val today = c.now.toLocal(c.zone).date
        return listOf(today, today.plus(1, DateTimeUnit.DAY))
            .flatMap { d -> c.learnedSlots.map { s -> atMinute(d, s.promptMinute, c.zone) to s } }
            .filter { (at, _) -> at >= floor && at < base }
            .filterNot { (at, _) -> c.quiet.contains(at, c.zone) }
            .filterNot { (at, _) -> c.lastEntryAt != null && c.lastEntryAt >= at - c.recentLogGraceMs }
            .minByOrNull { it.first }
            ?.let { (at, s) -> PlannedPrompt(at, PromptTrigger.LEARNED_MEAL, s.label) }
    }
}
