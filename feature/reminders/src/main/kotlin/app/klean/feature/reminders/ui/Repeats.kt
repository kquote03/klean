package app.klean.feature.reminders.ui

import app.klean.shared.time.RecurrenceRule
import app.klean.shared.time.RecurrenceRule.Freq
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** The choices in the repeat picker. Anything the presets can't say shows as [CUSTOM] with its RRULE text. */
enum class RepeatKind { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, EVERY_N_DAYS, EVERY_N_WEEKS, CUSTOM }

data class RepeatChoice(val kind: RepeatKind, val n: Int = 2, val custom: String = "")

/** Maps between stored RRULE text and the picker. Pure. */
object Repeats {
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    fun choiceOf(rule: String?): RepeatChoice {
        if (rule.isNullOrBlank()) return RepeatChoice(RepeatKind.NONE)
        val r = RecurrenceRule.parse(rule) ?: return RepeatChoice(RepeatKind.CUSTOM, custom = rule)
        val custom = RepeatChoice(RepeatKind.CUSTOM, custom = r.format())
        if (r.count != null || r.until != null || r.byMonth != null && r.freq != Freq.YEARLY) return custom
        val days = r.byDay.map { it.day }.toSet()
        val plainDays = r.byDay.all { it.ordinal == null }
        return when (r.freq) {
            Freq.DAILY -> when {
                r.byDay.isNotEmpty() -> custom
                r.interval == 1 -> RepeatChoice(RepeatKind.DAILY)
                else -> RepeatChoice(RepeatKind.EVERY_N_DAYS, r.interval)
            }
            Freq.WEEKLY -> when {
                !plainDays -> custom
                r.interval == 1 && days == WEEKDAYS -> RepeatChoice(RepeatKind.WEEKDAYS)
                days.size > 1 -> custom
                r.interval == 1 -> RepeatChoice(RepeatKind.WEEKLY)
                else -> RepeatChoice(RepeatKind.EVERY_N_WEEKS, r.interval)
            }
            Freq.MONTHLY -> if (r.interval == 1 && r.byDay.isEmpty()) RepeatChoice(RepeatKind.MONTHLY) else custom
            Freq.YEARLY -> if (r.interval == 1 && r.byDay.isEmpty()) RepeatChoice(RepeatKind.YEARLY) else custom
        }
    }

    /** The RRULE for [c], anchored on [on] (weekday, day of month). Null for "doesn't repeat" or an invalid custom rule. */
    fun ruleFor(c: RepeatChoice, on: LocalDate): String? = when (c.kind) {
        RepeatKind.NONE -> null
        RepeatKind.DAILY -> RecurrenceRule.daily().format()
        RepeatKind.WEEKDAYS -> RecurrenceRule.weekdays().format()
        RepeatKind.WEEKLY -> RecurrenceRule.weekly(on).format()
        RepeatKind.MONTHLY -> RecurrenceRule.monthly(on).format()
        RepeatKind.YEARLY -> RecurrenceRule.yearly(on).format()
        RepeatKind.EVERY_N_DAYS -> RecurrenceRule.everyNDays(c.n.coerceAtLeast(1)).format()
        RepeatKind.EVERY_N_WEEKS -> RecurrenceRule.everyNWeeks(c.n.coerceAtLeast(1), setOf(on.dayOfWeek)).format()
        RepeatKind.CUSTOM -> RecurrenceRule.parse(c.custom)?.format()
    }

    fun isValidCustom(text: String) = RecurrenceRule.parse(text) != null
}
