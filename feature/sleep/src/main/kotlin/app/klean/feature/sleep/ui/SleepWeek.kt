package app.klean.feature.sleep.ui

import android.content.Context
import app.klean.core.common.ZoneProvider
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.ui.module.WeekSummary
import app.klean.core.ui.module.WeekSummaryProvider
import app.klean.feature.sleep.R
import app.klean.feature.sleep.data.Night
import app.klean.feature.sleep.data.SleepMath
import app.klean.feature.sleep.data.SleepRepository
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * Seven nights, oldest first: one bar each (its length against the longest; null for a night with nothing logged),
 * and the average over the nights that were logged. No targets, no labels on length.
 */
data class SleepWeek(val days: List<LocalDate>, val bars: List<Float?>, val averageMs: Long?, val nights: Int) {
    companion object {
        /** [nights] newest first, as [SleepMath.nights] returns them; the first seven are used. */
        fun of(nights: List<Night>): SleepWeek {
            val week = nights.take(7).reversed()
            val lengths = week.map { n -> n.durationMs?.takeIf { it > 0 } }
            val longest = lengths.maxOfOrNull { it ?: 0 }?.coerceAtLeast(1) ?: 1
            val logged = lengths.filterNotNull()
            return SleepWeek(
                days = week.map { it.date },
                bars = lengths.map { l -> l?.let { it.toFloat() / longest } },
                averageMs = if (logged.isEmpty()) null else logged.sum() / logged.size,
                nights = logged.size,
            )
        }
    }
}

/** Night names and week copy: "Sunday into Monday", "on average, over 6 nights". */
class SleepWeekText(private val ctx: Context, private val fmt: SleepFormatter) {
    private fun weekday(d: LocalDate) = java.time.DayOfWeek.of(d.dayOfWeek.isoDayNumber).getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** A night by the day it ended on: "Sunday into Monday" within the week, else the date ("Mon 2 Nov"). */
    fun night(date: LocalDate, today: LocalDate): String =
        if (date.toEpochDays() > today.toEpochDays() - 7) {
            ctx.getString(R.string.sleep_night_into, weekday(date.minus(DatePeriod(days = 1))), weekday(date))
        } else {
            fmt.day(date, today)
        }

    fun average(w: SleepWeek): String? = w.averageMs?.let { ctx.getString(R.string.sleep_week_fact, fmt.duration(it)) }

    fun over(w: SleepWeek): String = ctx.resources.getQuantityString(R.plurals.sleep_week_over, w.nights, w.nights)

    fun description(w: SleepWeek): String = ctx.getString(R.string.sleep_week_bars, over(w))
}

/** Sleep on Track › Week: the average night of the last seven, with a bar a night; nothing without a night. */
class SleepWeekSummary @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<SleepRepository>,
    private val schedule: dagger.Lazy<SleepScheduleRepository>,
    private val fmt: SleepFormatter,
    private val zone: ZoneProvider,
) : WeekSummaryProvider {
    override val moduleId: ModuleId = ModuleId.SLEEP

    override fun week(days: List<LocalDate>): Flow<WeekSummary?> =
        combine(repo.get().observeSessions(), schedule.get().quietHours) { sessions, q ->
            val w = SleepWeek.of(SleepMath.nights(sessions, days.last(), 7, q, zone.current()))
            val text = SleepWeekText(ctx, fmt)
            text.average(w)?.let { fact -> WeekSummary(moduleId, ctx.getString(R.string.sleep_label), fact, text.over(w), w.bars, text.description(w)) }
        }
}
