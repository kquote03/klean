package app.klean.feature.reminders

import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SNOOZED
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.RecurrenceRule
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

enum class Snooze { TEN_MINUTES, ONE_HOUR, TOMORROW }

/** A recurring reminder's next instance: when, and the rule left after this one (COUNT goes down). */
data class NextOccurrence(val fireAt: Long, val recurrence: String?)

/** Pure reminder time maths. All instants are UTC ms. */
object ReminderTiming {
    /** When the reminder should go off: its snooze time while snoozed, otherwise its own time. */
    fun dueAt(r: ReminderEntity): Long = if (r.state == SNOOZED) r.snoozedUntil ?: r.fireAt else r.fireAt

    /**
     * When to arm the system alarm. Something already due goes off now. Pings wait for quiet hours to
     * end ([nextAllowed]); alarms the user set ring at their time regardless.
     */
    inline fun armAt(r: ReminderEntity, now: Long, nextAllowed: (Long) -> Long): Long {
        val at = maxOf(dueAt(r), now)
        return if (r.isAlarm) at else nextAllowed(at)
    }

    fun snoozeUntil(option: Snooze, now: Long, r: ReminderEntity, zone: TimeZone): Long = when (option) {
        Snooze.TEN_MINUTES -> now + 10 * MINUTE_MS
        Snooze.ONE_HOUR -> now + HOUR_MS
        Snooze.TOMORROW -> {
            val time = r.fireAt.toLocal(zone).time
            LocalDateTime(now.toLocal(zone).date.plus(DatePeriod(days = 1)), time).toInstant(zone).toEpochMilliseconds()
        }
    }

    /** A time the user picked (minute of day): today if it's still ahead, otherwise tomorrow. */
    fun pickedTime(minuteOfDay: Int, now: Long, zone: TimeZone): Long {
        val today = now.toLocal(zone).date
        val at = atMinute(today, minuteOfDay, zone)
        return if (at > now) at else atMinute(today.plus(DatePeriod(days = 1)), minuteOfDay, zone)
    }

    /**
     * The first instance of a recurring reminder after [now], at the same wall-clock time in the zone it was
     * set in. Instances that passed while nobody was looking are skipped (and use up COUNT). Null when the
     * reminder doesn't repeat, its rule can't be read, or the series is over.
     */
    fun nextOccurrence(r: ReminderEntity, now: Long): NextOccurrence? {
        var rule = r.recurrence?.let(RecurrenceRule::parse) ?: return null
        val tz = runCatching { TimeZone.of(r.zone) }.getOrDefault(TimeZone.UTC)
        val local = r.fireAt.toLocal(tz)
        var date = local.date
        repeat(MAX_STEPS) {
            val next = rule.next(date) ?: return null
            rule = rule.remainingAfterOne() ?: return null
            val at = LocalDateTime(next, local.time).toInstant(tz).toEpochMilliseconds()
            if (at > now) return NextOccurrence(at, rule.format())
            date = next
        }
        return null
    }

    private const val MAX_STEPS = 10_000
}
