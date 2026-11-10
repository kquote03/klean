package app.klean.feature.reminders

import android.content.Context
import android.text.format.DateFormat
import app.klean.core.common.ZoneProvider
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/** Times and days for reminders, in the device zone and the user's 12/24-hour setting. */
class ReminderFormatter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val zone: ZoneProvider,
    private val clock: Clock,
) {
    fun string(res: Int, vararg args: Any): String = ctx.getString(res, *args)

    fun time(ms: Long): String {
        val f = DateFormat.getTimeFormat(ctx)
        f.timeZone = TimeZone.getTimeZone(zone.current().id)
        return f.format(Date(ms))
    }

    /** "Wed 11 Nov". */
    fun day(ms: Long): String {
        val f = java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEdMMM"), java.util.Locale.getDefault())
        f.timeZone = TimeZone.getTimeZone(zone.current().id)
        return f.format(Date(ms))
    }

    /** "17:00", "Tomorrow 09:00", or "Fri 12 Nov 09:00". */
    fun dayTime(ms: Long): String {
        val tz = zone.current()
        val today = clock.now().toEpochMilliseconds().toLocal(tz).date
        val d = ms.toLocal(tz).date
        return when (d) {
            today -> time(ms)
            today.plus(DatePeriod(days = 1)) -> ctx.getString(R.string.reminders_tomorrow_at, time(ms))
            else -> {
                val f = java.text.SimpleDateFormat(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEdMMM"), java.util.Locale.getDefault())
                f.timeZone = TimeZone.getTimeZone(tz.id)
                f.format(Date(ms)) + " " + time(ms)
            }
        }
    }
}
