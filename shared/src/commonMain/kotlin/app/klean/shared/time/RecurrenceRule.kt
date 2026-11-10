package app.klean.shared.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * A date-level RRULE (RFC 5545) subset, shared by Tasks, Reminders and Calendar. Times of day are the
 * caller's business: a rule only says on which local dates something recurs.
 *
 * Supported: `FREQ=DAILY|WEEKLY|MONTHLY|YEARLY`, `INTERVAL`, `BYDAY` (plain days; with an ordinal such as
 * `2MO`/`-1FR` only for MONTHLY), `BYMONTHDAY` (1..31 or -1 = last day; MONTHLY/YEARLY), `BYMONTH` (YEARLY,
 * one month), `COUNT` or `UNTIL` (a date, or a UTC date-time whose date is used). `WKST` is accepted and
 * ignored (weeks start on Monday). Anything else makes [parse] return null, so a custom rule the user
 * types is either followed exactly or refused.
 *
 * Unlike RFC 5545, a month day that doesn't exist in a month (the 31st in February) is clamped to the last
 * day rather than skipped: "monthly on the 31st" means "end of each month" to people.
 *
 * A series starts at a `start` date (DTSTART). Occurrences are the dates on or after `start` that the rule
 * produces; `start` itself is one only if it matches. [COUNT][count] counts from `start`.
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: Int? = null,
    val byMonth: Int? = null,
    val count: Int? = null,
    val until: LocalDate? = null,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    /**
     * What a month day that doesn't exist in a month (the 31st in April, Feb 29 in a common year) becomes:
     * [CLAMP] moves it to the last day (tasks, "end of each month"); [SKIP] leaves that month out (RFC 5545,
     * calendars).
     */
    enum class MissingDay { CLAMP, SKIP }

    /** A BYDAY entry: [ordinal] is the n-th (1..5) or n-th last (-1..-5) such day of the month, or null for every one. */
    data class WeekdayNum(val day: DayOfWeek, val ordinal: Int? = null)

    init {
        require(interval >= 1) { "interval must be >= 1" }
        require(count == null || until == null) { "COUNT and UNTIL are exclusive" }
    }

    /** Canonical RRULE value (no "RRULE:" prefix), in a fixed key order. */
    fun format(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(",") { (it.ordinal?.toString() ?: "") + CODES[it.day.isoDayNumber - 1] })
        byMonth?.let { add("BYMONTH=$it") }
        byMonthDay?.let { add("BYMONTHDAY=$it") }
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=" + it.year.toString().padStart(4, '0') + two(it.month.number) + two(it.day)) }
    }.joinToString(";")

    override fun toString(): String = format()

    /**
     * The first occurrence strictly after [after], or null when the series has ended. [start] is the series
     * start (DTSTART); without it the series is taken to start at [after], which then counts as the first
     * occurrence for COUNT (a task's due date, for example).
     */
    fun next(after: LocalDate, start: LocalDate? = null): LocalDate? =
        sequence(start ?: after).firstOrNull { it > after }

    /** Every occurrence of the series starting at [start] that falls in [from]..[to], both inclusive. */
    fun occurrences(
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 1000,
        missingDay: MissingDay = MissingDay.CLAMP,
    ): List<LocalDate> =
        sequence(start, missingDay).dropWhile { it < from }.takeWhile { it <= to }.take(limit).toList()

    /**
     * The rule for the rest of a series after one occurrence is used up: COUNT goes down by one, and null
     * means nothing is left. Tasks carry the rule from one instance to the next this way.
     */
    fun remainingAfterOne(): RecurrenceRule? = when {
        count == null -> this
        count <= 1 -> null
        else -> copy(count = count - 1)
    }

    /** Occurrences on or after [start], in order, honouring COUNT and UNTIL. */
    fun sequence(start: LocalDate, missingDay: MissingDay = MissingDay.CLAMP): Sequence<LocalDate> {
        val base = when (freq) {
            Freq.DAILY -> periods(MAX_PERIODS * 7) { k -> listOf(start.plus(DatePeriod(days = k * interval))) }
                .filter { d -> byDay.isEmpty() || byDay.any { it.day == d.dayOfWeek } }
            Freq.WEEKLY -> {
                val days = byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }.sortedBy { it.isoDayNumber }
                val monday = start.minus(DatePeriod(days = start.dayOfWeek.isoDayNumber - 1))
                periods(MAX_PERIODS) { k ->
                    val week = monday.plus(DatePeriod(days = 7 * k * interval))
                    days.map { week.plus(DatePeriod(days = it.isoDayNumber - 1)) }
                }
            }
            Freq.MONTHLY -> periods(MAX_PERIODS) { k ->
                val first = LocalDate(start.year, start.month.number, 1).plus(DatePeriod(months = k * interval))
                inMonth(first.year, first.month.number, start, missingDay)
            }
            Freq.YEARLY -> periods(MAX_PERIODS / 4) { k ->
                val y = start.year + k * interval
                inMonth(y, byMonth ?: start.month.number, start, missingDay)
            }
        }.filter { it >= start }
        val bounded = until?.let { u -> base.takeWhile { it <= u } } ?: base
        return count?.let { bounded.take(it) } ?: bounded
    }

    /** The rule's dates in one month, ascending. */
    private fun inMonth(year: Int, month: Int, start: LocalDate, missingDay: MissingDay): List<LocalDate> {
        val len = monthLength(year, month)
        if (byDay.isNotEmpty() && freq == Freq.MONTHLY) {
            val first = LocalDate(year, month, 1)
            return byDay.flatMap { wd ->
                val offset = (wd.day.isoDayNumber - first.dayOfWeek.isoDayNumber + 7) % 7
                val all = (1 + offset..len step 7).map { LocalDate(year, month, it) }
                when (val n = wd.ordinal) {
                    null -> all
                    else -> listOfNotNull(if (n > 0) all.getOrNull(n - 1) else all.getOrNull(all.size + n))
                }
            }.sorted()
        }
        val wanted = byMonthDay ?: start.day
        if (missingDay == MissingDay.SKIP && wanted > len) return emptyList()
        val day = if (wanted < 0) len + 1 + wanted else minOf(wanted, len)
        return listOf(LocalDate(year, month, day.coerceIn(1, len)))
    }

    private fun periods(max: Int, f: (Int) -> List<LocalDate>): Sequence<LocalDate> =
        (0 until max).asSequence().flatMap { f(it) }

    companion object {
        /** How many periods (days/weeks/months, years ×4) a sequence looks ahead before giving up. */
        private const val MAX_PERIODS = 4000
        private val CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
        private val WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        private val BYDAY = Regex("""^([+-]?[1-5])?(MO|TU|WE|TH|FR|SA|SU)$""")
        private val UNTIL = Regex("""^(\d{4})(\d{2})(\d{2})(T\d{6}Z?)?$""")

        private fun two(n: Int) = n.toString().padStart(2, '0')

        fun daily() = RecurrenceRule(Freq.DAILY)
        fun weekdays() = RecurrenceRule(Freq.WEEKLY, byDay = WEEKDAYS.map { WeekdayNum(it) })
        /** Every week on [on]'s weekday. */
        fun weekly(on: LocalDate) = RecurrenceRule(Freq.WEEKLY, byDay = listOf(WeekdayNum(on.dayOfWeek)))
        /** Every month on [on]'s day of month (the 31st clamps to the last day). */
        fun monthly(on: LocalDate) = RecurrenceRule(Freq.MONTHLY, byMonthDay = on.day)
        /** Every year on [on]'s month and day (Feb 29 clamps to Feb 28). */
        fun yearly(on: LocalDate) = RecurrenceRule(Freq.YEARLY, byMonth = on.month.number, byMonthDay = on.day)
        fun everyNDays(n: Int) = RecurrenceRule(Freq.DAILY, interval = n)
        /** Every [n] weeks; on [days], or on the start's weekday when empty. */
        fun everyNWeeks(n: Int, days: Set<DayOfWeek> = emptySet()) =
            RecurrenceRule(Freq.WEEKLY, interval = n, byDay = days.sortedBy { it.isoDayNumber }.map { WeekdayNum(it) })

        /** Parses an RRULE value (an optional "RRULE:" prefix, any case), or null when it isn't one this class follows. */
        fun parse(text: String): RecurrenceRule? {
            val body = text.trim().uppercase().removePrefix("RRULE:").trim()
            if (body.isEmpty()) return null
            val parts = body.split(';').filter { it.isNotBlank() }.map { p ->
                val i = p.indexOf('=')
                if (i <= 0) return null
                p.substring(0, i).trim() to p.substring(i + 1).trim()
            }
            if (parts.map { it.first }.toSet().size != parts.size) return null
            val m = parts.toMap()
            val freq = m["FREQ"]?.let { f -> Freq.entries.firstOrNull { it.name == f } } ?: return null
            val known = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL", "WKST")
            if (m.keys.any { it !in known }) return null
            val interval = m["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null } ?: 1
            val byDay = m["BYDAY"]?.let { v ->
                v.split(',').map { code ->
                    val match = BYDAY.matchEntire(code.trim()) ?: return null
                    val ord = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
                    if (ord != null && freq != Freq.MONTHLY) return null
                    WeekdayNum(DayOfWeek(CODES.indexOf(match.groupValues[2]) + 1), ord)
                }.distinct().sortedWith(compareBy({ it.day.isoDayNumber }, { it.ordinal ?: 0 }))
            } ?: emptyList()
            val byMonthDay = m["BYMONTHDAY"]?.let { v ->
                val n = v.toIntOrNull() ?: return null
                if (freq != Freq.MONTHLY && freq != Freq.YEARLY) return null
                if (n == 0 || n !in -1..31) return null
                n
            }
            val byMonth = m["BYMONTH"]?.let { v ->
                if (freq != Freq.YEARLY) return null
                v.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            }
            if (byMonthDay != null && byDay.isNotEmpty()) return null
            val count = m["COUNT"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null }
            val until = m["UNTIL"]?.let { v ->
                val g = UNTIL.matchEntire(v)?.groupValues ?: return null
                runCatching { LocalDate(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull() ?: return null
            }
            if (count != null && until != null) return null
            return RecurrenceRule(freq, interval, byDay, byMonthDay, byMonth, count, until)
        }

        private fun monthLength(year: Int, month: Int): Int {
            val first = LocalDate(year, month, 1)
            return first.daysUntil(first.plus(DatePeriod(months = 1)))
        }
    }
}
