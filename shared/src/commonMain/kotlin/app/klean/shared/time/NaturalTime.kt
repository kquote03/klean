package app.klean.shared.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What a short phrase says about time. All fields are local wall-clock values.
 * Whenever [minutes] (minute of day, 0..1439) is set, [date] is set too. [rest] is the input with the
 * time words removed, dangling "at"/"on" dropped and whitespace collapsed.
 */
data class ParsedTime(val date: LocalDate?, val minutes: Int?, val rest: String) {
    /** Date and time together, or null when the phrase gave no time of day. */
    val dateTime: LocalDateTime?
        get() = if (date != null && minutes != null) LocalDateTime(date, LocalTime(minutes / 60, minutes % 60)) else null
}

/**
 * Offline parser for the time expressions people type into quick capture. Pure and deterministic:
 * the caller supplies `now` in local time.
 */
object NaturalTime {
    /**
     * Understands, case-insensitively: "today", "tonight" (20:00), "tomorrow", weekday names ("friday" = next
     * occurrence; today only if a time later today is given), "next week" (+7 days), "in 2 hours", "in 30 min",
     * "in 3 days", "at 5" (1–6 read as pm; 7–12 pick the next future occurrence), "at 5pm", "5:30", "17:30",
     * "6pm", "noon", "midnight", "March 3", "3 March", "3/3" (day/month, UAE order, optional /year).
     * A date without a year that has already passed this year rolls to next year. A time with no date that has
     * already passed today means tomorrow.
     */
    fun parse(text: String, now: LocalDateTime): ParsedTime {
        var work = text
        fun take(re: Regex): MatchResult? = re.find(work)?.also { work = work.replaceRange(it.range, " ") }

        val today = now.date
        val nowMin = now.hour * 60 + now.minute

        // 1. "in N hours/minutes/days/weeks"
        var durDateTime: LocalDateTime? = null
        var durDate: LocalDate? = null
        take(DURATION)?.let { m ->
            val n = m.groupValues[1].lowercase().let { if (it == "a" || it == "an") 1 else it.toInt() }
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("h") -> durDateTime = now.plusDuration(n.hours)
                unit.startsWith("m") -> durDateTime = now.plusDuration(n.minutes)
                unit.startsWith("d") -> durDate = today.plus(n, DateTimeUnit.DAY)
                else -> durDate = today.plus(n * 7, DateTimeUnit.DAY)
            }
        }

        // 2. Explicit dates.
        var explicit: LocalDate? = null
        for (re in listOf(SLASH_DATE, MONTH_DAY, DAY_MONTH)) {
            val m = re.find(work) ?: continue
            val (day, month, year) = when (re) {
                SLASH_DATE -> Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3])
                MONTH_DAY -> Triple(m.groupValues[2].toInt(), monthNumber(m.groupValues[1]), m.groupValues[3])
                else -> Triple(m.groupValues[1].toInt(), monthNumber(m.groupValues[2]), m.groupValues[3])
            }
            val resolved = resolveDate(day, month, year.takeIf { it.isNotEmpty() }?.toInt(), today) ?: continue
            explicit = resolved
            work = work.replaceRange(m.range, " ")
            break
        }

        // 3. Relative day words.
        var relative: LocalDate? = null
        var tonight = false
        take(RELATIVE)?.let { m ->
            when (m.groupValues[1].lowercase()) {
                "today" -> relative = today
                "tonight" -> { relative = today; tonight = true }
                "tomorrow", "tmrw" -> relative = today.plus(1, DateTimeUnit.DAY)
                else -> relative = today.plus(7, DateTimeUnit.DAY) // next week
            }
        }
        val weekdayDiff: Int? = take(WEEKDAY)?.let { m ->
            val target = WEEKDAYS.indexOf(m.groupValues[1].lowercase())
            (target - today.dayOfWeek.ordinal + 7) % 7
        }

        // 4. Time of day.
        val time: ClockTime? = parseTime(::take)

        // The date we would land on before considering the time; null = "today, or tomorrow if passed".
        val fixedDate: LocalDate? = explicit ?: relative ?: durDate ?: durDateTime?.date
        val todayLike = if (fixedDate != null) fixedDate == today else (weekdayDiff == null || weekdayDiff == 0)

        var minutes: Int? = null
        var rollIfPassed = false
        if (durDateTime != null && time == null) {
            minutes = durDateTime!!.hour * 60 + durDateTime!!.minute
        } else if (time != null) {
            val h = time.hour; val mm = time.minute
            when {
                !time.ambiguous -> { minutes = h * 60 + mm; rollIfPassed = true }
                tonight -> minutes = (if (h < 12) h + 12 else h) * 60 + mm
                h in 1..6 -> { minutes = (h + 12) * 60 + mm; rollIfPassed = true }
                !todayLike -> minutes = h * 60 + mm
                else -> {
                    val candidates = if (h == 12) listOf(12 * 60 + mm) else listOf(h * 60 + mm, (h + 12) * 60 + mm)
                    minutes = candidates.firstOrNull { it > nowMin } ?: candidates.first()
                    rollIfPassed = true
                }
            }
        } else if (tonight) {
            minutes = 20 * 60
        }

        val date: LocalDate? = when {
            fixedDate != null -> fixedDate
            weekdayDiff != null -> {
                val diff = if (weekdayDiff == 0 && (minutes == null || minutes <= nowMin)) 7 else weekdayDiff
                today.plus(diff, DateTimeUnit.DAY)
            }
            minutes != null -> if (rollIfPassed && minutes <= nowMin) today.plus(1, DateTimeUnit.DAY) else today
            else -> null
        }
        return ParsedTime(date, minutes, cleanRest(work))
    }

    private class ClockTime(val hour: Int, val minute: Int, val ambiguous: Boolean)

    private fun parseTime(take: (Regex) -> MatchResult?): ClockTime? {
        take(MERIDIEM_TIME)?.let { m ->
            val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (h in 1..12 && mm < 60) {
                val pm = m.groupValues[3].lowercase().startsWith("p")
                return ClockTime(h % 12 + if (pm) 12 else 0, mm, ambiguous = false)
            }
        }
        take(COLON_TIME)?.let { m -> clock(m.groupValues[1], m.groupValues[2].toInt())?.let { return it } }
        take(AT_HOUR)?.let { m -> clock(m.groupValues[1], 0)?.let { return it } }
        take(NAMED_TIME)?.let { m ->
            return ClockTime(if (m.groupValues[1].lowercase() == "noon") 12 else 0, 0, ambiguous = false)
        }
        return null
    }

    private fun clock(hourText: String, minute: Int): ClockTime? {
        val h = hourText.toInt()
        if (h > 23 || minute > 59) return null
        val ambiguous = h in 1..12 && !hourText.startsWith("0")
        return ClockTime(h, minute, ambiguous)
    }

    private fun resolveDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year != null) return runCatching { LocalDate(if (year < 100) 2000 + year else year, month, day) }.getOrNull()
        // Roll forward to the next year where the date exists and is not in the past (Feb 29 may skip years).
        for (y in today.year..today.year + 8) {
            val d = runCatching { LocalDate(y, month, day) }.getOrNull() ?: continue
            if (d >= today) return d
        }
        return null
    }

    private fun monthNumber(name: String): Int {
        val n = name.lowercase().take(3)
        return MONTH_PREFIXES.indexOf(n) + 1
    }

    private fun LocalDateTime.plusDuration(d: Duration): LocalDateTime =
        toInstant(TimeZone.UTC).plus(d).toLocalDateTime(TimeZone.UTC)

    private fun cleanRest(s: String): String {
        var r = s.replace(WHITESPACE, " ").trim()
        while (true) {
            val next = r.replace(DANGLING_END, "").replace(DANGLING_START, "").trim().trim(',', ';', '-').trim()
            if (next == r) break
            r = next
        }
        return r
    }

    private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val MONTH_PREFIXES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH =
        "(january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"
    private const val PRE = """(?:\b(?:at|on)\s+)?"""
    private const val ORD = "(?:st|nd|rd|th)?"
    private val OPTS = setOf(RegexOption.IGNORE_CASE)

    private val DURATION = Regex("""\bin\s+(an?|\d{1,4})\s+(hours?|hrs?|minutes?|mins?|days?|weeks?)\b""", OPTS)
    private val SLASH_DATE = Regex("""$PRE(?<![\w/:.])(\d{1,2})/(\d{1,2})(?:/(\d{4}|\d{2}))?(?![\w/:])""", OPTS)
    private val MONTH_DAY = Regex("""$PRE\b$MONTH\.?\s+(\d{1,2})$ORD(?![\w/:])(?:,?\s+(\d{4})\b)?""", OPTS)
    private val DAY_MONTH = Regex("""$PRE(?<![\w/:.])(\d{1,2})$ORD\s+(?:of\s+)?$MONTH\b(?:,?\s+(\d{4})\b)?""", OPTS)
    private val RELATIVE = Regex("""$PRE\b(today|tonight|tomorrow|tmrw|next\s+week)\b""", OPTS)
    private val WEEKDAY =
        Regex("""$PRE\b(?:(?:next|this)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", OPTS)
    private val MERIDIEM_TIME =
        Regex("""$PRE(?<![\w/:.])(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)(?!\w)""", OPTS)
    private val COLON_TIME = Regex("""$PRE(?<![\w/:.])(\d{1,2}):(\d{2})(?![\w/:])""", OPTS)
    private val AT_HOUR = Regex("""\bat\s+(\d{1,2})(?![\w/:.])""", OPTS)
    private val NAMED_TIME = Regex("""$PRE\b(noon|midnight)\b""", OPTS)
    private val WHITESPACE = Regex("""\s+""")
    private val DANGLING_END = Regex("""\s*\b(?:at|on)$""", OPTS)
    private val DANGLING_START = Regex("""^(?:at|on)\b\s*""", OPTS)
}
