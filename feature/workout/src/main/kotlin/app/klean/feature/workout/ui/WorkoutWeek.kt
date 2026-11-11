package app.klean.feature.workout.ui

import android.content.Context
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.WeekSummary
import app.klean.core.ui.module.WeekSummaryProvider
import app.klean.feature.workout.R
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

/**
 * Finished sessions over seven [days] (oldest first): how many, their minutes, and one bar per day (that day's
 * minutes against the longest day; a session without a length still shows a half bar). Neutral facts only.
 */
data class WorkoutWeek(val days: List<LocalDate>, val sessions: Int, val minutes: Int, val bars: List<Float?>, val lastAt: Long?) {
    companion object {
        fun of(all: List<SessionWithSets>, days: List<LocalDate>, dateOf: (Long) -> LocalDate): WorkoutWeek {
            val inWeek = all.filter { !it.isOpen && dateOf(it.session.startedAt) in days }
            val byDay = inWeek.groupBy { dateOf(it.session.startedAt) }
            val dayMinutes = days.map { d -> byDay[d]?.let { l -> l.sumOf { SessionRows.minutes(it) ?: 0 } } }
            val longest = dayMinutes.maxOfOrNull { it ?: 0 }?.coerceAtLeast(1) ?: 1
            val bars = dayMinutes.map { m -> m?.let { if (it == 0) HALF else it.toFloat() / longest } }
            return WorkoutWeek(days, inWeek.size, inWeek.sumOf { SessionRows.minutes(it) ?: 0 }, bars, inWeek.maxOfOrNull { it.session.startedAt })
        }

        private const val HALF = 0.5f
    }
}

/** Week copy for Workout: "2 sessions", "75 min, last on Thursday". */
class WorkoutWeekText @Inject constructor(@ApplicationContext private val ctx: Context, private val fmt: WorkoutFormatter) {
    fun sessions(n: Int): String = ctx.resources.getQuantityString(R.plurals.workout_week_sessions, n, n)

    /** "2 sessions, 75 min" for the Workout screen's hero. */
    fun headline(w: WorkoutWeek): String =
        if (w.minutes > 0) ctx.getString(R.string.workout_week_headline, sessions(w.sessions), fmt.duration(w.minutes)) else sessions(w.sessions)

    /**
     * "75 min, last on Thursday"; "last today" and "last yesterday" for the newest days. Without [minutes] (the
     * headline already has them): "Last on Thursday".
     */
    fun supporting(w: WorkoutWeek, today: LocalDate, minutes: Boolean = true): String? {
        val at = w.lastAt ?: return null
        val day = fmt.localDate(at)
        val last = when (day.daysUntil(today)) {
            0 -> ctx.getString(R.string.workout_week_last_today)
            1 -> ctx.getString(R.string.workout_week_last_yesterday)
            else -> ctx.getString(R.string.workout_week_last_on, java.time.DayOfWeek.of(day.dayOfWeek.isoDayNumber).getDisplayName(TextStyle.FULL, Locale.getDefault()))
        }
        return if (minutes && w.minutes > 0) ctx.getString(R.string.workout_week_supporting, fmt.duration(w.minutes), last) else last.replaceFirstChar { it.titlecase() }
    }

    fun description(w: WorkoutWeek): String = ctx.getString(R.string.workout_week_bars, sessions(w.sessions))
}

/** Workout on Track › Week: the week's sessions, or nothing when there were none. */
class WorkoutWeekSummary @Inject constructor(
    private val repo: dagger.Lazy<WorkoutRepository>,
    private val text: WorkoutWeekText,
    @ApplicationContext private val ctx: Context,
    private val zone: ZoneProvider,
) : WeekSummaryProvider {
    override val moduleId: ModuleId = ModuleId.WORKOUT

    override fun week(days: List<LocalDate>): Flow<WeekSummary?> = repo.get().observeSessions().map { all ->
        val w = WorkoutWeek.of(all, days) { it.toLocal(zone.current()).date }
        if (w.sessions == 0) {
            null
        } else {
            WeekSummary(moduleId, ctx.getString(R.string.workout_week_label), text.sessions(w.sessions), text.supporting(w, days.last()), w.bars, text.description(w))
        }
    }
}
