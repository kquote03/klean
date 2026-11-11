package app.klean.shared.diet

import kotlin.math.roundToInt

/** What the calorie slot (or the end of a food text) says. See plan 2026-11-11 "calories without the counting". */
sealed interface CalorieInput {
    data object Empty : CalorieInput
    data object Invalid : CalorieInput
    /** The user knows the calories. */
    data class Kcal(val kcal: Int) : CalorieInput
    /** The user knows how much, not the calories: Klean fills them in for this amount. */
    data class Grams(val grams: Int) : CalorieInput
    /** "150x100x200": [kcal] per [perGrams], ate [ateGrams] → [total]. */
    data class PerAmount(val kcal: Int, val perGrams: Int, val ateGrams: Int, val total: Int) : CalorieInput

    companion object {
        const val MAX_KCAL = 20_000
        /** Capture extras key for the calorie slot (kept here so core/capture needn't depend on diet). */
        const val EXTRA_KEY = "diet.kcal"
        private const val NUM = """(\d+(?:[.,]\d+)?)"""
        private val KCAL = Regex("""^$NUM\s*(?:k?cal(?:ories)?)?$""", RegexOption.IGNORE_CASE)
        private val GRAMS = Regex("""^$NUM\s*(g|gr|grams?|kg)$""", RegexOption.IGNORE_CASE)
        private val PER = Regex(
            """^$NUM\s*(?:k?cal)?\s*[x×*]\s*$NUM\s*(?:g|gr|grams?)?\s*[x×*]\s*$NUM\s*(?:g|gr|grams?)?$""",
            RegexOption.IGNORE_CASE,
        )
        private val TRAILING = Regex(
            """^(.*?\S)\s+((?:\d[\d.,]*\s*(?:k?cal(?:ories)?|g|gr|grams?|kg))|(?:\d[\d.,]*\s*(?:k?cal)?\s*[x×*]\s*\d[\d.,]*\s*(?:g|gr|grams?)?\s*[x×*]\s*\d[\d.,]*\s*(?:g|gr|grams?)?))$""",
            RegexOption.IGNORE_CASE,
        )

        private fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

        fun parse(raw: String): CalorieInput {
            val s = raw.trim()
            if (s.isEmpty()) return Empty
            PER.matchEntire(s)?.let { m ->
                val (k, per, ate) = m.destructured
                val kcal = num(k) ?: return Invalid
                val perG = num(per) ?: return Invalid
                val ateG = num(ate) ?: return Invalid
                if (kcal <= 0 || perG <= 0 || ateG <= 0) return Invalid
                val total = (kcal * ateG / perG).roundToInt()
                return if (total in 1..MAX_KCAL) PerAmount(kcal.roundToInt(), perG.roundToInt(), ateG.roundToInt(), total) else Invalid
            }
            GRAMS.matchEntire(s)?.let { m ->
                val (n, unit) = m.destructured
                val g = (num(n) ?: return Invalid) * (if (unit.equals("kg", true)) 1000 else 1)
                return if (g >= 1 && g <= 10_000) Grams(g.roundToInt()) else Invalid
            }
            KCAL.matchEntire(s)?.let { m ->
                val k = num(m.groupValues[1]) ?: return Invalid
                return if (k >= 1 && k <= MAX_KCAL) Kcal(k.roundToInt()) else Invalid
            }
            return Invalid
        }

        /** "rice 200g" → ("rice", Grams(200)). A bare trailing number stays part of the food. */
        fun splitTrailing(text: String): Pair<String, CalorieInput> {
            val m = TRAILING.matchEntire(text.trim()) ?: return text.trim() to Empty
            val parsed = parse(m.groupValues[2])
            return if (parsed is Kcal || parsed is Grams || parsed is PerAmount) m.groupValues[1].trim() to parsed else text.trim() to Empty
        }
    }
}
