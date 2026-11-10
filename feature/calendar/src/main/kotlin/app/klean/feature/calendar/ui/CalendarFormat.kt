package app.klean.feature.calendar.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime

/** Date and time labels for the calendar screens, in the device zone and locale. */
class CalendarFormat(private val is24h: Boolean, private val zone: TimeZone = TimeZone.currentSystemDefault()) {
    private val locale get() = Locale.getDefault()

    fun time(ms: Long): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).time.toJavaLocalTime()
        return t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))
    }

    /** Minute of day as a label, "09:30" / "9:30 AM". */
    fun minutes(m: Int): String =
        java.time.LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))

    fun hour(h: Int): String =
        java.time.LocalTime.of(h, 0).format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", locale))

    /** "Tue 10 Nov". */
    fun shortDay(d: LocalDate): String = d.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    /** "Tuesday 10 November 2026". */
    fun longDay(d: LocalDate): String = d.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))

    /** "November 2026". */
    fun month(d: LocalDate): String = d.toJavaLocalDate().format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    /** "9 – 15 Nov". */
    fun range(from: LocalDate, to: LocalDate): String {
        val f = from.toJavaLocalDate()
        val t = to.toJavaLocalDate()
        val left = if (f.month == t.month) f.format(DateTimeFormatter.ofPattern("d", locale)) else f.format(DateTimeFormatter.ofPattern("d MMM", locale))
        return "$left – " + t.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }

    /** "Tue" for the weekday of [d]. */
    fun weekdayShort(d: LocalDate): String = d.toJavaLocalDate().format(DateTimeFormatter.ofPattern("EEE", locale))

    /** "10". */
    fun dayNumber(d: LocalDate): String = d.toJavaLocalDate().format(DateTimeFormatter.ofPattern("d", locale))
}

@Composable
fun rememberCalendarFormat(): CalendarFormat {
    val ctx = LocalContext.current
    val is24h = DateFormat.is24HourFormat(ctx)
    return remember(is24h) { CalendarFormat(is24h) }
}
