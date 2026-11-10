package app.klean.feature.reminders.ui

import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SCHEDULED
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SNOOZED
import app.klean.feature.reminders.RINGING
import app.klean.feature.reminders.ReminderTiming
import app.klean.shared.time.NaturalTime
import app.klean.shared.time.toLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

data class ReminderRow(
    val id: String,
    val title: String,
    /** When it goes off next (its snooze time while snoozed). */
    val at: Long,
    val isAlarm: Boolean,
    val recurring: Boolean,
    val snoozed: Boolean,
    val taskId: String?,
)

/** The Reminders tab: "Earlier" holds the ones that went off and weren't handled, shown neutrally. */
data class ReminderGroups(
    val earlier: List<ReminderRow> = emptyList(),
    val today: List<ReminderRow> = emptyList(),
    val tomorrow: List<ReminderRow> = emptyList(),
    val later: List<ReminderRow> = emptyList(),
) {
    val isEmpty get() = earlier.isEmpty() && today.isEmpty() && tomorrow.isEmpty() && later.isEmpty()

    companion object {
        fun build(all: List<ReminderEntity>, now: Long, zone: TimeZone): ReminderGroups {
            fun row(r: ReminderEntity) = ReminderRow(
                r.id, r.title, ReminderTiming.dueAt(r), r.isAlarm, r.recurrence != null, r.state == SNOOZED, r.taskId,
            )
            val live = all.filter { !it.sync.deleted }
            val today = now.toLocal(zone).date
            val tomorrow = today.plus(DatePeriod(days = 1))
            val upcoming = live.filter { it.state == SCHEDULED || it.state == SNOOZED }.map(::row).sortedBy { it.at }
            return ReminderGroups(
                earlier = live.filter { it.state == RINGING }.map(::row).sortedByDescending { it.at },
                today = upcoming.filter { it.at.toLocal(zone).date <= today },
                tomorrow = upcoming.filter { it.at.toLocal(zone).date == tomorrow },
                later = upcoming.filter { it.at.toLocal(zone).date > tomorrow },
            )
        }
    }
}

/** A reminder as typed or captured: "remind me at 5 to call Ahmed", "alarm 6:30 gym", "wake me tomorrow 7". */
data class ReminderDraft(val title: String, val fireAt: Long, val isAlarm: Boolean)

object ReminderText {
    private val ALARM = Regex("""\balarm\b|\bwake me\b""", RegexOption.IGNORE_CASE)
    private val LEAD = Regex("""^(?:remind me(?:\s+to)?|set (?:an? )?(?:alarm|reminder)(?:\s+for)?|alarm(?:\s+for)?|wake me(?:\s+up)?|reminder:?)\s*""", RegexOption.IGNORE_CASE)
    private val TRAIL_TO = Regex("""^(?:to|that|about)\s+""", RegexOption.IGNORE_CASE)

    /** No time given: tomorrow at [defaultMinute]. A date without a time: that day at [defaultMinute]. */
    fun parse(text: String, now: Long, zone: TimeZone, defaultMinute: Int = 9 * 60): ReminderDraft {
        val local = now.toLocal(zone)
        val p = NaturalTime.parse(text.trim(), local)
        val date = p.date ?: local.date.plus(DatePeriod(days = 1))
        val minute = p.minutes ?: defaultMinute
        val at = LocalDateTime(date, kotlinx.datetime.LocalTime(minute / 60, minute % 60)).toInstant(zone).toEpochMilliseconds()
        var title = p.rest.trim()
        repeat(2) { title = title.replace(LEAD, "").replace(TRAIL_TO, "").trim() }
        return ReminderDraft(title.ifEmpty { text.trim() }, at, ALARM.containsMatchIn(text))
    }
}
