package app.klean.feature.workout.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.workout.R
import app.klean.feature.workout.WorkoutKleanModule
import app.klean.feature.workout.WorkoutStartSheetRoute
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.feature.workout.ui.WorkoutFormatter
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Workout on Today's whiteboard: a running session needs attention ("Workout in progress · 24 min"), today's
 * finished sessions are in "so far today" with what was done and how long, and the one-tap button opens the start
 * sheet (saved workouts, Run, an empty workout).
 */
@Singleton
class WorkoutGlanceProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<WorkoutRepository>,
    private val fmt: WorkoutFormatter,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.WORKOUT

    private val minutes: Flow<Long> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(60_000)
        }
    }

    override fun glance(): Flow<GlanceContribution> = combine(repo.get().observeSessions(), minutes) { sessions, now ->
        val today = now.toLocal(zone.current()).date
        val (open, finished) = sessions.partition { it.isOpen }
        GlanceContribution(
            attention = open.map {
                val mins = ((now - it.session.startedAt) / 60_000).toInt().coerceAtLeast(0)
                GlanceRow(
                    "workout:${it.session.id}", moduleId, ctx.getString(R.string.workout_glance_running), it.session.startedAt,
                    detail = fmt.duration(mins), icon = Icons.Rounded.FitnessCenter, route = WorkoutKleanModule.DEEP_LINK,
                )
            },
            done = finished.filter { fmt.localDate(it.session.startedAt) == today }.map {
                GlanceRow(
                    "workout:${it.session.id}", moduleId, fmt.names(it), it.session.endedAt ?: it.session.startedAt,
                    detail = fmt.summary(it), icon = Icons.Rounded.FitnessCenter, route = WorkoutKleanModule.DEEP_LINK,
                )
            },
            quickAction = QuickAction.Open(moduleId, ctx.getString(R.string.workout_glance_quick), Icons.Rounded.FitnessCenter, WorkoutStartSheetRoute),
        )
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class WorkoutGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun workoutGlanceProvider(p: WorkoutGlanceProvider): TodayGlanceProvider
}
