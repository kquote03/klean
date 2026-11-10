package app.klean.feature.workout.ui

import android.content.Context
import android.text.format.DateFormat
import app.klean.core.common.ZoneProvider
import app.klean.feature.workout.R
import app.klean.feature.workout.data.SessionWithSets
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Display text for workouts, in the device zone: "18:10", "Today", "Sat", "3 items · 25 min". */
class WorkoutFormatter @Inject constructor(@ApplicationContext private val ctx: Context, private val zone: ZoneProvider) {
    fun time(at: Long): String {
        val f = DateFormat.getTimeFormat(ctx)
        f.timeZone = TimeZone.getTimeZone(zone.current().id)
        return f.format(Date(at))
    }

    fun localDate(at: Long): LocalDate = at.toLocal(zone.current()).date

    private fun pattern(skeleton: String, at: Long): String {
        val f = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), Locale.getDefault())
        f.timeZone = TimeZone.getTimeZone(zone.current().id)
        return f.format(Date(at))
    }

    /** "Today", "Yesterday", "Sat" within the week, else "Wed 25 Nov". */
    fun day(at: Long, today: LocalDate): String {
        val diff = localDate(at).daysUntil(today)
        return when (diff) {
            0 -> ctx.getString(R.string.workout_day_today)
            1 -> ctx.getString(R.string.workout_day_yesterday)
            in 2..6 -> pattern("EEE", at)
            else -> pattern("EEEdMMM", at)
        }
    }

    /** "Today 18:10". */
    fun dayTime(at: Long, today: LocalDate): String = "${day(at, today)} ${time(at)}"

    fun duration(minutes: Int): String =
        if (minutes >= 60) ctx.getString(R.string.workout_hours_minutes, minutes / 60, minutes % 60) else ctx.getString(R.string.workout_minutes, minutes)

    /** "3 items · 25 min", "1 item", or "Logged" for a note-only session. */
    fun summary(s: SessionWithSets): String {
        val n = SessionRows.itemCount(s)
        val items = if (n == 0) s.session.notes ?: ctx.getString(R.string.workout_note_only) else ctx.resources.getQuantityString(R.plurals.workout_items, n, n)
        val mins = SessionRows.minutes(s) ?: return items
        return ctx.getString(R.string.workout_summary_join, items, duration(mins))
    }

    /** What the session was, in a line: "pushups, squats, plank". */
    fun names(s: SessionWithSets): String =
        s.sets.map { it.exercise.trim() }.distinctBy { it.lowercase() }.joinToString(", ").ifEmpty { s.session.notes ?: ctx.getString(R.string.workout_note_only) }
}
