package app.klean.shared.diet

import app.klean.shared.time.DayWindow
import app.klean.shared.time.MINUTE_MS

data class Gap(val start: Long, val end: Long, val ongoing: Boolean) {
    /** Middle of the gap on a 5-minute step: where "Add something" presets the time. */
    val midpoint: Long
        get() {
            val step = 5 * MINUTE_MS
            val mid = start + (end - start) / 2
            return ((mid + step / 2) / step) * step
        }
}

/** Neutral gap detection: reports long stretches without entries inside the wake window. No judgement. */
object GapDetector {
    fun find(entryTimes: List<Long>, window: DayWindow, now: Long, thresholdMs: Long): List<Gap> {
        val upper = minOf(window.wakeEnd, now)
        if (upper <= window.wakeStart) return emptyList()
        val points = buildList {
            add(window.wakeStart)
            addAll(entryTimes.filter { it > window.wakeStart && it < upper }.sorted())
            add(upper)
        }
        return points.zipWithNext()
            .filter { (a, b) -> b - a >= thresholdMs }
            .map { (a, b) -> Gap(a, b, ongoing = b == now && now < window.wakeEnd) }
    }
}
