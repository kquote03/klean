package app.klean.feature.diet.calories

import app.klean.shared.diet.KcalSource
import kotlin.math.roundToInt

/** An earlier entry with calories, as history sees it. */
data class PastEntry(val text: String, val kcal: Int, val grams: Int?, val source: KcalSource, val eatenAt: Long)

/** Lowercase, apostrophes dropped, punctuation to spaces, spaces collapsed: "McDonald's  Cheeseburger." = "mcdonalds cheeseburger". */
fun normaliseFood(s: String): String =
    s.lowercase().replace("'", "").replace("\u2019", "").replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Calories for [food] from earlier entries of the same food (exact match after [normaliseFood]), newest first.
 * With [grams], only a past entry that had grams counts, scaled per gram. HISTORY-sourced rows are ignored,
 * so estimates never compound. Null when nothing fits (the AI, if assigned, tries next). On-device only.
 */
fun historyKcal(food: String, grams: Int?, past: List<PastEntry>): Int? {
    val key = normaliseFood(food)
    if (key.isEmpty()) return null
    val same = past.filter { it.source != KcalSource.HISTORY && normaliseFood(it.text) == key }.sortedByDescending { it.eatenAt }
    if (grams != null) {
        val withGrams = same.firstOrNull { it.grams != null && it.grams > 0 } ?: return null
        return (withGrams.kcal.toDouble() / withGrams.grams!! * grams).roundToInt().takeIf { it > 0 }
    }
    return same.firstOrNull()?.kcal
}
