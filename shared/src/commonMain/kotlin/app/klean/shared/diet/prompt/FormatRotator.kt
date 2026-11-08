package app.klean.shared.diet.prompt

import kotlin.random.Random

data class PromptHistoryItem(val format: PromptFormat, val phrasingId: String, val outcome: PromptOutcome)

object FormatRotator {
    private val cycle = PromptFormat.entries
    fun nextFormat(newestFirst: List<PromptHistoryItem>): PromptFormat {
        val last = newestFirst.firstOrNull { it.outcome != PromptOutcome.PENDING } ?: return PromptFormat.STANDARD
        return if (last.outcome == PromptOutcome.LOGGED) last.format
        else cycle[(cycle.indexOf(last.format) + 1) % cycle.size]
    }
    fun nextPhrasing(pool: List<String>, newestFirst: List<PromptHistoryItem>, random: Random): String {
        require(pool.isNotEmpty())
        val recent = newestFirst.take(3).map { it.phrasingId }.toSet()
        return pool.filterNot { it in recent }.ifEmpty { pool }.random(random)
    }
}
