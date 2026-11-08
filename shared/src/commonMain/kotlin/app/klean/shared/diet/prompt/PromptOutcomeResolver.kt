package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS

object PromptOutcomeResolver {
    const val LOG_WINDOW_MS = 60 * MINUTE_MS
    const val IGNORE_AFTER_MS = 2 * HOUR_MS
    /** entryCreatedTimes = when entries were *logged* (created_at), not eaten_at, so backdating doesn't count. */
    fun resolve(sentAt: Long, entryCreatedTimes: List<Long>, now: Long): PromptOutcome = when {
        entryCreatedTimes.any { it in sentAt..(sentAt + LOG_WINDOW_MS) } -> PromptOutcome.LOGGED
        now - sentAt >= IGNORE_AFTER_MS -> PromptOutcome.IGNORED
        else -> PromptOutcome.PENDING
    }
}
