package app.klean.shared.diet.prompt

import app.klean.shared.time.DAY_MINUTES
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.toLocal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

object MealTimeLearner {
    const val MIN_DISTINCT_DAYS = 7
    const val WINDOW_DAYS = 21
    private const val BIN = 30
    private const val BINS = DAY_MINUTES / BIN
    private const val COVERAGE = 0.4
    private const val PROMPT_OFFSET_MIN = 45
    private const val MAX_SLOTS = 4
    private const val MIN_SEPARATION_BINS = 4

    fun learn(entryTimes: List<Long>, now: Long, zone: TimeZone): List<MealSlot> {
        val since = now - WINDOW_DAYS * 24 * HOUR_MS
        val locals = entryTimes.filter { it in since..now }.map { it.toLocal(zone) }
        val days = locals.map { it.date }.toSet()
        if (days.size < MIN_DISTINCT_DAYS) return emptyList()
        val binOf = { l: LocalDateTime -> (l.hour * 60 + l.minute) / BIN }
        val daysPerBin = IntArray(BINS)
        locals.groupBy(binOf).forEach { (b, l) -> daysPerBin[b] = l.map { it.date }.toSet().size }
        fun raw(i: Int) = daysPerBin[(i + BINS) % BINS]
        val smooth = IntArray(BINS) { raw(it - 1) + 2 * raw(it) + raw(it + 1) }
        fun s(i: Int) = smooth[(i + BINS) % BINS]
        fun coverage(i: Int): Int {
            val bins = setOf((i - 1 + BINS) % BINS, i, (i + 1) % BINS)
            return locals.filter { binOf(it) in bins }.map { it.date }.toSet().size
        }
        val minDays = days.size * COVERAGE
        val peaks = (0 until BINS)
            .filter { s(it) > 0 && s(it) >= s(it - 1) && s(it) > s(it + 1) && coverage(it) >= minDays }
            .sortedByDescending { s(it) }
        val chosen = mutableListOf<Int>()
        for (p in peaks) {
            if (chosen.size == MAX_SLOTS) break
            if (chosen.none { circular(it, p) < MIN_SEPARATION_BINS }) chosen += p
        }
        return chosen.sorted().map { b ->
            val c = b * BIN + BIN / 2
            MealSlot(c, (c + PROMPT_OFFSET_MIN) % DAY_MINUTES, labelFor(c))
        }
    }
    private fun circular(a: Int, b: Int) = minOf((a - b + BINS) % BINS, (b - a + BINS) % BINS)
    fun labelFor(minute: Int): MealLabel = when (minute) {
        in 240..659 -> MealLabel.BREAKFAST
        in 660..959 -> MealLabel.LUNCH
        in 960..1319 -> MealLabel.DINNER
        else -> MealLabel.LATE
    }
}
