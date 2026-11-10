package app.klean.feature.contacts.birthday

import app.klean.feature.contacts.data.Birthday
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Birthday dates, pure. A Feb 29 birthday falls on Feb 28 in years without one. */
object Birthdays {
    /** The day [b] is celebrated in [year]. */
    fun inYear(b: Birthday, year: Int): LocalDate =
        if (b.month == 2 && b.day == 29 && !Birthday.isLeap(year)) LocalDate(year, 2, 28) else LocalDate(year, b.month, b.day)

    /** The next birthday on or after [today] (today counts). */
    fun next(b: Birthday, today: LocalDate): LocalDate {
        val thisYear = inYear(b, today.year)
        return if (thisYear >= today) thisYear else inYear(b, today.year + 1)
    }

    /** Days from [today] to the next birthday; 0 on the day. */
    fun daysUntil(b: Birthday, today: LocalDate): Int = (next(b, today).toEpochDays() - today.toEpochDays()).toInt()

    /** Every celebration day in [from]..[to], both inclusive. */
    fun between(b: Birthday, from: LocalDate, to: LocalDate): List<LocalDate> =
        (from.year..to.year).map { inYear(b, it) }.filter { it in from..to && (b.year == null || it.year >= b.year) }

    /** The age turned on [on], when the birth year is known (null otherwise, or before birth). */
    fun ageOn(b: Birthday, on: LocalDate): Int? = b.year?.let { y -> (on.year - y).takeIf { it >= 0 } }

    /** The day a reminder [daysBefore] the celebration on [day] goes off. */
    fun reminderDate(day: LocalDate, daysBefore: Int): LocalDate = day.minus(DatePeriod(days = daysBefore))
}
