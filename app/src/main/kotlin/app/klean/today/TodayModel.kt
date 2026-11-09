package app.klean.today

import java.time.DayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Calm, time-of-day greeting. Night says hello rather than telling anyone to sleep. */
enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

fun greetingFor(hour: Int): Greeting = when (hour) {
    in 5..11 -> Greeting.MORNING
    in 12..16 -> Greeting.AFTERNOON
    in 17..21 -> Greeting.EVENING
    else -> Greeting.NIGHT
}

/**
 * The seven days of the week containing [day], shifted by [offset] weeks (0 = this week).
 * Compares by ordinal (Monday = 0 in both java.time and kotlinx.datetime), so either enum works.
 */
fun weekOf(day: LocalDate, firstDay: DayOfWeek, offset: Int = 0): List<LocalDate> {
    val back = (day.dayOfWeek.ordinal - firstDay.ordinal + 7) % 7
    val start = day.minus(back, DateTimeUnit.DAY).plus(offset * 7, DateTimeUnit.DAY)
    return (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
}

/** The overview may look back but never ahead of the current week. */
fun clampWeekOffset(offset: Int): Int = minOf(0, offset)
