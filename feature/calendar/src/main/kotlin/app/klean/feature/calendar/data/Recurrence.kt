package app.klean.feature.calendar.data

import app.klean.shared.ical.ICalTime
import app.klean.shared.time.RecurrenceRule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Calendar occurrences on top of the shared [RecurrenceRule] (dates), adding what only a calendar needs: the
// time of day in the event's zone, a date-time UNTIL, EXDATE and overlap with a window. A month day that a month
// lacks is skipped (RFC 5545), unlike tasks, which clamp it.

internal object RecurrenceExpander {
    private val UNTIL_PART = Regex("(?i)(?:^|;)UNTIL=([^;]+)")

    /** The rule for the dates; its UNTIL is applied here instead, to the minute, in the event's zone. */
    fun parse(rrule: String): RecurrenceRule? = RecurrenceRule.parse(rrule)?.copy(until = null)

    /**
     * Starts (UTC ms) of the occurrences of an event first starting at [startMs] (wall time in [zone]) that
     * overlap [fromMs, toMs). [durationMs] decides overlap; a zero-length event counts at its start. A rule this
     * expander can't read shows the first occurrence only.
     */
    fun occurrences(
        startMs: Long,
        durationMs: Long,
        rrule: String?,
        exdates: Set<Long>,
        zone: TimeZone,
        fromMs: Long,
        toMs: Long,
    ): List<Long> {
        fun overlaps(s: Long) = s < toMs && s + maxOf(durationMs, 1L) > fromMs
        val rule = rrule?.let(::parse)
            ?: return if (overlaps(startMs) && startMs !in exdates) listOf(startMs) else emptyList()
        val untilMs = UNTIL_PART.find(rrule)?.groupValues?.get(1)?.let { untilMs(it, zone) }
        val first = Instant.fromEpochMilliseconds(startMs).toLocalDateTime(zone)
        val out = ArrayList<Long>()
        for (date in rule.sequence(first.date, RecurrenceRule.MissingDay.SKIP)) {
            val local = LocalDateTime(date, first.time)
            val ms = local.toInstant(zone).toEpochMilliseconds()
            if (untilMs != null && ms > untilMs) break
            if (ms >= toMs) break
            if (ms in exdates) continue
            if (overlaps(ms)) out += ms
        }
        return out
    }

    private fun untilMs(raw: String, zone: TimeZone): Long? = runCatching {
        val v = raw.trim()
        if (v.length == 8) {
            val d = LocalDate(v.substring(0, 4).toInt(), v.substring(4, 6).toInt(), v.substring(6, 8).toInt())
            d.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1
        } else {
            ICalTime(v).toEpochMs(zone)
        }
    }.getOrNull()
}

/** The repeat choices the event editor offers; anything else is kept as written ([CUSTOM]). */
enum class Repeat(val rrule: String?) {
    NONE(null), DAILY("FREQ=DAILY"), WEEKLY("FREQ=WEEKLY"), MONTHLY("FREQ=MONTHLY"), YEARLY("FREQ=YEARLY"), CUSTOM(null);

    companion object {
        fun of(rrule: String?): Repeat =
            if (rrule.isNullOrBlank()) NONE else entries.firstOrNull { it.rrule == rrule.removePrefix("RRULE:") } ?: CUSTOM
    }
}
