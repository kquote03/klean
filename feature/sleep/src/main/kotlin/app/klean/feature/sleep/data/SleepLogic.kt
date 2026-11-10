package app.klean.feature.sleep.data

import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.shared.time.DayWindows
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One logical day on the Sleep screen: the night that ended on it, or a blank. */
data class Night(val date: LocalDate, val session: SleepSessionEntity?) {
    val durationMs: Long? get() = session?.let { SleepMath.duration(it) }
}

/** Pure sleep arithmetic. Days are logical days (DayWindows): a night belongs to the day it ends in. */
object SleepMath {
    fun duration(s: SleepSessionEntity): Long? = s.wakeAt?.let { (it - s.bedAt).coerceAtLeast(0) }

    fun hoursMinutes(ms: Long): Pair<Int, Int> {
        val minutes = (ms / MINUTE_MS).toInt()
        return minutes / 60 to minutes % 60
    }

    fun wakeDay(s: SleepSessionEntity, q: QuietHours, zone: TimeZone): LocalDate? = s.wakeAt?.let { DayWindows.logicalDate(it, q, zone) }

    /**
     * "Last night": the longest finished session whose wake falls in the logical day [today], so a nap later
     * in the day doesn't replace the night (the plan said "latest"; a 20-minute nap shown as last night read oddly).
     */
    fun lastNight(sessions: List<SleepSessionEntity>, today: LocalDate, q: QuietHours, zone: TimeZone): SleepSessionEntity? =
        sessions.filter { !it.sync.deleted && wakeDay(it, q, zone) == today }.maxByOrNull { duration(it) ?: 0 }

    /** The last [count] logical days ending with [today], newest first; each holds its longest finished session. */
    fun nights(sessions: List<SleepSessionEntity>, today: LocalDate, count: Int, q: QuietHours, zone: TimeZone): List<Night> {
        val byDay = sessions.filter { !it.sync.deleted && it.wakeAt != null }.groupBy { wakeDay(it, q, zone) }
        return (0 until count).map { i ->
            val d = today.minus(DatePeriod(days = i))
            Night(d, byDay[d]?.maxByOrNull { duration(it) ?: 0 })
        }
    }

    /** The latest instant at or before [ref] whose local minute of day is one of [minutes]. */
    fun latestAtOrBefore(ref: Long, minutes: List<Int>, zone: TimeZone, strict: Boolean = false): Long {
        val date = ref.toLocal(zone).date
        return listOf(date.plus(DatePeriod(days = 1)), date, date.minus(DatePeriod(days = 1)), date.minus(DatePeriod(days = 2)))
            .flatMap { d -> minutes.map { atMinute(d, it, zone) } }
            .filter { if (strict) it < ref else it <= ref }
            .max()
    }
}

/** What a sleep phrase asks for. Times are UTC ms. */
sealed interface SleepCommand {
    data class GoToBed(val at: Long) : SleepCommand
    data class WakeUp(val at: Long) : SleepCommand
    data class Night(val bedAt: Long, val wakeAt: Long) : SleepCommand
}

/**
 * Offline sleep phrases for capture: "slept at 11 woke at 7", "slept 1-9", "went to bed at 10:30pm",
 * "woke up at 6:45", "going to bed", "I'm up", "slept 7 hours". A time without am/pm is whichever of its
 * two readings happened last (a bedtime before the wake time), so "slept at 11 woke at 7" is 23:00–07:00.
 */
object SleepText {
    private val OPTS = setOf(RegexOption.IGNORE_CASE)
    private val DURATION = Regex("""\bslept\s+(?:for\s+)?(\d+(?:[.,]\d+)?)\s*(?:h|hr|hrs|hours?)\b""", OPTS)
    private val TIME = Regex("""(?<![\d:.])(\d{1,2})(?::([0-5]\d))?\s*(am|pm|a\.m\.|p\.m\.)?(?![\d:])""", OPTS)
    private val MIDNIGHT = Regex("""\bmidnight\b""", OPTS)
    private val BED = Regex("""\bslept\b|\bbed\b|\bbedtime\b|\basleep\b|\bfrom\b""", OPTS)
    private val WAKE = Regex("""\bwoke\b|\bwake\b|\bup\b|\bto\b|\buntil\b|\btill\b|-|–""", OPTS)
    private val WAKE_ALONE = Regex("""\bwoke\b|\bwake\b|\bi'?m up\b|\bgot up\b|\bup now\b""", OPTS)
    private val BED_ALONE = Regex("""\bgoing to bed\b|\bbedtime\b|\bgoing to sleep\b|\bwent to bed\b|\bheading to bed\b""", OPTS)

    private data class Token(val index: Int, val minutes: List<Int>)

    fun parse(text: String, now: Long, zone: TimeZone, schedule: QuietHours): SleepCommand {
        val t = text.trim()
        DURATION.find(t)?.let { m ->
            val ms = (m.groupValues[1].replace(',', '.').toDouble() * HOUR_MS).toLong()
            val todayWake = atMinute(now.toLocal(zone).date, schedule.endMinute, zone)
            val wake = if (!schedule.isDisabled && todayWake <= now) todayWake else now
            return SleepCommand.Night(wake - ms, wake)
        }
        val tokens = tokens(t)
        if (tokens.size >= 2) {
            val wake = SleepMath.latestAtOrBefore(now, tokens[1].minutes, zone)
            val bed = SleepMath.latestAtOrBefore(wake, tokens[0].minutes, zone, strict = true)
            return SleepCommand.Night(bed, wake)
        }
        if (tokens.size == 1) {
            val tok = tokens[0]
            val at = SleepMath.latestAtOrBefore(now, tok.minutes, zone)
            val before = t.substring(0, tok.index)
            val wakeAt = WAKE.findAll(before).lastOrNull()?.range?.first ?: -1
            val bedAt = BED.findAll(before).lastOrNull()?.range?.first ?: -1
            return if (wakeAt > bedAt) SleepCommand.WakeUp(at) else SleepCommand.GoToBed(at)
        }
        return when {
            BED_ALONE.containsMatchIn(t) -> SleepCommand.GoToBed(now)
            WAKE_ALONE.containsMatchIn(t) -> SleepCommand.WakeUp(now)
            // "slept", "slept well": the night just ended.
            else -> SleepCommand.WakeUp(now)
        }
    }

    private fun tokens(t: String): List<Token> {
        val out = TIME.findAll(t).mapNotNull { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val ampm = m.groupValues[3].lowercase().replace(".", "")
            if (h > 23 || (ampm.isNotEmpty() && (h == 0 || h > 12))) return@mapNotNull null
            val hours = when {
                ampm == "am" -> listOf(h % 12)
                ampm == "pm" -> listOf(h % 12 + 12)
                h in 1..12 -> listOf(h % 12, h % 12 + 12)
                else -> listOf(h)
            }
            Token(m.range.first, hours.map { it * 60 + min })
        }.toMutableList()
        MIDNIGHT.findAll(t).forEach { out += Token(it.range.first, listOf(0)) }
        return out.sortedBy { it.index }
    }
}
