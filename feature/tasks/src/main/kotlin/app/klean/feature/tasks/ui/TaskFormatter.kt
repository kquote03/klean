package app.klean.feature.tasks.ui

import android.content.Context
import android.text.format.DateFormat
import app.klean.core.common.ZoneProvider
import app.klean.feature.tasks.R
import app.klean.shared.time.atMinute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Due labels: "Today", "Today 14:00", "Tomorrow", "Fri 14:00", "Wed 25 Nov". Never "overdue". */
class TaskFormatter @Inject constructor(@ApplicationContext private val ctx: Context, private val zone: ZoneProvider) {
    private fun tz() = zone.current()

    fun time(date: LocalDate, minutes: Int): String {
        val f = DateFormat.getTimeFormat(ctx)
        f.timeZone = TimeZone.getTimeZone(tz().id)
        return f.format(Date(atMinute(date, minutes, tz())))
    }

    private fun pattern(skeleton: String, date: LocalDate): String {
        val f = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
        f.timeZone = TimeZone.getTimeZone(tz().id)
        return f.format(Date(atMinute(date, 12 * 60, tz())))
    }

    /** A day on its own: "Today", "Tomorrow", "Yesterday", a weekday within the week, else "Wed 25 Nov". */
    fun day(date: LocalDate, today: LocalDate): String {
        val diff = today.daysUntil(date)
        return when {
            diff == 0 -> ctx.getString(R.string.tasks_day_today)
            diff == 1 -> ctx.getString(R.string.tasks_day_tomorrow)
            diff == -1 -> ctx.getString(R.string.tasks_day_yesterday)
            diff in 2..6 -> pattern("EEEE", date)
            else -> pattern("EEEdMMM", date)
        }
    }

    fun due(date: LocalDate?, minutes: Int?, today: LocalDate): String? {
        date ?: return null
        val d = if (date in today..today.plus(DatePeriod(days = 6)) && date.minus(today) > 1) pattern("EEE", date) else day(date, today)
        return if (minutes != null) "$d ${time(date, minutes)}" else d
    }

    /** "from Fri" for a task in the review. */
    fun from(date: LocalDate?, today: LocalDate): String? = date?.let { ctx.getString(R.string.tasks_review_from, day(it, today)) }

    private fun LocalDate.minus(other: LocalDate) = other.daysUntil(this)
}
