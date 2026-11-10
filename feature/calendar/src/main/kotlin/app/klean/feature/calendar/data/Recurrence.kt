package app.klean.feature.calendar.data

import app.klean.shared.ical.ICalTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// TODO(after M18 merge): replace with RecurrenceRule (shared/.../time/RecurrenceRule.kt, owned by the Tasks lane).
// This private expander follows RFC 5545 for the subset Klean writes and most ICS files use: FREQ
// DAILY/WEEKLY/MONTHLY/YEARLY, INTERVAL, BYDAY (weekdays for WEEKLY/DAILY, "2TU"/"-1FR" for MONTHLY), COUNT,
// UNTIL, plus EXDATE. Unlike the Tasks rule (which clamps), a monthly 31st skips shorter months, as calendars do.

internal enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday with an optional ordinal within the month (MONTHLY: 2 = second, -1 = last). */
internal data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

internal data class CalendarRecurrence(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val count: Int? = null,
    /** Raw UNTIL value (date or date-time). */
    val until: String? = null,
)

internal object RecurrenceExpander {
    private const val MAX_STEPS = 20_000
    private val DAYS = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )

    fun parse(rrule: String): CalendarRecurrence? {
        val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i).trim().uppercase() to p.substring(i + 1).trim()
        }.toMap()
        val freq = parts["FREQ"]?.uppercase()?.let { f -> Freq.entries.firstOrNull { it.name == f } } ?: return null
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { token ->
            val m = Regex("^([+-]?\\d{1,2})?([A-Za-z]{2})$").find(token.trim()) ?: return@mapNotNull null
            val day = DAYS[m.groupValues[2].uppercase()] ?: return@mapNotNull null
            WeekdayNum(day, m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt())
        }.orEmpty()
        return CalendarRecurrence(
            freq = freq,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            byDay = byDay,
            count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 },
            until = parts["UNTIL"],
        )
    }

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
        val untilMs = rule.until?.let { untilMs(it, zone) }
        val first = Instant.fromEpochMilliseconds(startMs).toLocalDateTime(zone)
        val out = ArrayList<Long>()
        var produced = 0
        for (local in candidates(rule, first)) {
            val ms = local.toInstant(zone).toEpochMilliseconds()
            if (untilMs != null && ms > untilMs) break
            if (rule.count != null && produced >= rule.count) break
            produced++
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

    /** Every candidate start in order, from [first] on (never before it). */
    private fun candidates(rule: CalendarRecurrence, first: LocalDateTime): Sequence<LocalDateTime> = sequence {
        val time = first.time
        val start = first.date
        when (rule.freq) {
            Freq.DAILY -> {
                val days = rule.byDay.map { it.day }.toSet()
                for (k in 0 until MAX_STEPS) {
                    val d = start.plus(k * rule.interval, DateTimeUnit.DAY)
                    if (days.isEmpty() || d.dayOfWeek in days) yield(LocalDateTime(d, time))
                }
            }
            Freq.WEEKLY -> {
                val days = rule.byDay.map { it.day }.distinct().sortedBy { it.isoDayNumber }
                    .ifEmpty { listOf(start.dayOfWeek) }
                val weekStart = start.minus(start.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                for (k in 0 until MAX_STEPS) {
                    val week = weekStart.plus(k * rule.interval * 7, DateTimeUnit.DAY)
                    for (day in days) {
                        val d = week.plus(day.isoDayNumber - 1, DateTimeUnit.DAY)
                        if (d >= start) yield(LocalDateTime(d, time))
                    }
                }
            }
            Freq.MONTHLY -> {
                val ordinals = rule.byDay.filter { it.ordinal != null }
                for (k in 0 until MAX_STEPS) {
                    val month = LocalDate(start.year, start.month.number, 1).plus(k * rule.interval, DateTimeUnit.MONTH)
                    val dates = if (ordinals.isEmpty()) {
                        listOfNotNull(dateOrNull(month.year, month.month.number, start.day))
                    } else {
                        ordinals.mapNotNull { nthWeekday(month, it) }.sorted()
                    }
                    for (d in dates) if (d >= start) yield(LocalDateTime(d, time))
                }
            }
            Freq.YEARLY -> {
                for (k in 0 until MAX_STEPS) {
                    val d = dateOrNull(start.year + k * rule.interval, start.month.number, start.day) ?: continue
                    yield(LocalDateTime(d, time))
                }
            }
        }
    }

    private fun dateOrNull(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate(y, m, d) }.getOrNull()

    /** The [w] weekday of the month starting at [monthStart], e.g. second Tuesday, last Friday. */
    private fun nthWeekday(monthStart: LocalDate, w: WeekdayNum): LocalDate? {
        val n = w.ordinal ?: return null
        val next = monthStart.plus(1, DateTimeUnit.MONTH)
        val all = generateSequence(monthStart) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it < next }
            .filter { it.dayOfWeek == w.day }.toList()
        return if (n > 0) all.getOrNull(n - 1) else all.getOrNull(all.size + n)
    }
}

/** The repeat choices the event editor offers; anything else is kept as written ([CUSTOM]). */
enum class Repeat(val rrule: String?) {
    NONE(null), DAILY("FREQ=DAILY"), WEEKLY("FREQ=WEEKLY"), MONTHLY("FREQ=MONTHLY"), YEARLY("FREQ=YEARLY"), CUSTOM(null);

    companion object {
        fun of(rrule: String?): Repeat =
            if (rrule.isNullOrBlank()) NONE else entries.firstOrNull { it.rrule == rrule.removePrefix("RRULE:") } ?: CUSTOM
    }
}
