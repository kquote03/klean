package app.klean.shared.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class DayWindow(val date: LocalDate, val start: Long, val end: Long, val wakeStart: Long, val wakeEnd: Long)

object DayWindows {
    fun boundaryShiftMinutes(q: QuietHours): Int {
        if (q.isDisabled) return 0
        val len = (q.endMinute - q.startMinute + DAY_MINUTES) % DAY_MINUTES
        val mid = (q.startMinute + len / 2) % DAY_MINUTES
        return if (mid <= 720) mid else mid - DAY_MINUTES
    }
    fun logicalDate(epochMs: Long, q: QuietHours, zone: TimeZone): LocalDate =
        (epochMs - boundaryShiftMinutes(q) * MINUTE_MS).toLocal(zone).date
    fun forDate(date: LocalDate, q: QuietHours, zone: TimeZone): DayWindow {
        val shift = boundaryShiftMinutes(q) * MINUTE_MS
        val start = atMinute(date, 0, zone) + shift
        val end = atMinute(date.plus(1, DateTimeUnit.DAY), 0, zone) + shift
        if (q.isDisabled) return DayWindow(date, start, end, start, end)
        val wakeStart = q.nextAllowed(start, zone)
        val wakeEnd = listOf(date.minus(1, DateTimeUnit.DAY), date, date.plus(1, DateTimeUnit.DAY))
            .map { atMinute(it, q.startMinute, zone) }
            .filter { it > wakeStart }.minOrNull()?.coerceAtMost(end) ?: end
        return DayWindow(date, start, end, wakeStart, wakeEnd)
    }
}
