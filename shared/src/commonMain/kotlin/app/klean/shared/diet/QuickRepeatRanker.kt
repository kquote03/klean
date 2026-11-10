package app.klean.shared.diet

import app.klean.shared.time.HOUR_MS

data class RepeatCandidate(val text: String, val loggedAt: Long)

/** Ranks past entry texts by frequency with recency decay. Derived on demand, nothing to curate. */
object QuickRepeatRanker {
    private const val HALF_LIFE_MS = 3 * 24 * HOUR_MS
    fun rank(history: List<RepeatCandidate>, now: Long, limit: Int = 5): List<String> =
        history.filter { it.text.isNotBlank() && it.text.trim().length <= 40 }
            .groupBy { normalize(it.text) }
            .map { (_, g) -> g.maxBy { it.loggedAt }.text.trim() to g.sumOf { 1.0 / (1.0 + (now - it.loggedAt).toDouble() / HALF_LIFE_MS) } }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }
    fun normalize(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
}
