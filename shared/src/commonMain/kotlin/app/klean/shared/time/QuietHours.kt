package app.klean.shared.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable
data class QuietHours(val startMinute: Int, val endMinute: Int) {
    init { require(startMinute in 0 until DAY_MINUTES && endMinute in 0 until DAY_MINUTES) }
    val isDisabled: Boolean get() = startMinute == endMinute
    fun containsMinute(m: Int): Boolean = when {
        isDisabled -> false
        startMinute < endMinute -> m in startMinute until endMinute
        else -> m >= startMinute || m < endMinute
    }
    fun contains(epochMs: Long, zone: TimeZone): Boolean = containsMinute(minuteOfDay(epochMs, zone))
    /** First instant >= epochMs outside quiet hours. */
    fun nextAllowed(epochMs: Long, zone: TimeZone): Long {
        if (!contains(epochMs, zone)) return epochMs
        val local = epochMs.toLocal(zone)
        val m = local.hour * 60 + local.minute
        val date = if (startMinute > endMinute && m >= startMinute) local.date.plus(1, DateTimeUnit.DAY) else local.date
        return atMinute(date, endMinute, zone)
    }
    companion object { val DEFAULT = QuietHours(0, 8 * 60) }
}
