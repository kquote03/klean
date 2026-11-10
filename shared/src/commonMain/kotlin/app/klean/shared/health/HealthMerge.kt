package app.klean.shared.health

/** A time span in UTC epoch millis. */
data class Span(val start: Long, val end: Long) {
    val length: Long get() = (end - start).coerceAtLeast(0)
}

/** A workout for duplicate detection: Klean's own or one read from Health Connect. */
data class WorkoutSpan(val id: String, val span: Span, val hasRoute: Boolean)

/** Of two duplicate workouts, [keep] stays and [link] is only linked to it (by `hc_record_id`). */
data class Duplicate(val keep: String, val link: String)

/** How Health Connect records merge with Klean's own (M22). */
object HealthMerge {
    /** A manual night is replaced when Health Connect has a night overlapping it by more than this. */
    const val SLEEP_OVERLAP = 0.5
    /** Two workouts are one when they overlap by more than this. */
    const val WORKOUT_OVERLAP = 0.7

    /** Overlap as a share of the shorter span (0–1); empty spans never overlap. */
    fun overlap(a: Span, b: Span): Double {
        val shorter = minOf(a.length, b.length)
        if (shorter <= 0) return 0.0
        val shared = (minOf(a.end, b.end) - maxOf(a.start, b.start)).coerceAtLeast(0)
        return shared.toDouble() / shorter
    }

    /** Ids of the manual nights that Health Connect's nights replace (they're tombstoned with a note). */
    fun manualSleepToReplace(manual: List<Pair<String, Span>>, healthConnect: List<Span>): Set<String> =
        manual.filter { (_, m) -> healthConnect.any { overlap(m, it) > SLEEP_OVERLAP } }.map { it.first }.toSet()

    /** Whether [a] and [b] are the same workout, and which to keep (the one with a route, else [a]). */
    fun duplicate(a: WorkoutSpan, b: WorkoutSpan): Duplicate? {
        if (overlap(a.span, b.span) <= WORKOUT_OVERLAP) return null
        return if (b.hasRoute && !a.hasRoute) Duplicate(keep = b.id, link = a.id) else Duplicate(keep = a.id, link = b.id)
    }
}
