package app.klean.feature.tasks.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.History
import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.tasks.R
import app.klean.feature.tasks.TasksKleanModule
import app.klean.feature.tasks.data.TasksRepository
import app.klean.feature.tasks.ui.DayTicker
import app.klean.feature.tasks.ui.TaskViews
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

/**
 * Tasks on Today's whiteboard:
 * - **attention:** tasks still open from earlier days as one calm row ("3 from earlier days", opening Tasks, where
 *   "Still want these?" asks about them), plus today's timed tasks whose time has passed;
 * - **coming up:** today's other open tasks, tickable from Today;
 * - **so far today:** what was completed today, with its time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TasksGlanceProvider @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<TasksRepository>,
    private val days: DayTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.TASKS

    private val minutes: Flow<Long> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds())
            delay(60_000)
        }
    }

    override fun glance(): Flow<GlanceContribution> = days.days().flatMapLatest { d ->
        combine(repo.get().observeAll(), minutes) { all, now ->
            val v = TaskViews.build(all, emptyList(), d, null)
            val tz = zone.current()
            fun at(minute: Int?) = minute?.let { atMinute(d, it, tz) }
            val (passed, later) = v.today.partition { t -> at(t.dueMinutes)?.let { it < now } == true }
            val earlier = if (v.review.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    GlanceRow(
                        KEY_EARLIER, moduleId, ctx.resources.getQuantityString(R.plurals.tasks_glance_earlier, v.review.size, v.review.size),
                        at = null, icon = Icons.Rounded.History, route = TasksKleanModule.DEEP_LINK_TASKS,
                    ),
                )
            }
            fun row(t: app.klean.feature.tasks.ui.TaskRowModel) = GlanceRow(
                key = "task:${t.id}", module = moduleId, text = t.title, at = at(t.dueMinutes),
                detail = t.listName, icon = Icons.Rounded.CheckCircleOutline, route = TasksKleanModule.DEEP_LINK_TASKS, checkable = true,
            )
            val doneToday = all.filter { t ->
                val c = t.completedAt
                c != null && !t.sync.deleted && t.archivedAt == null && t.parentId == null && c.toLocal(tz).date == d
            }
            GlanceContribution(
                attention = earlier + passed.map(::row),
                upcoming = later.map(::row),
                done = doneToday.map { t ->
                    GlanceRow("task:${t.id}", moduleId, t.title, t.completedAt, icon = Icons.Rounded.CheckCircleOutline, route = TasksKleanModule.DEEP_LINK_TASKS)
                },
                quickAction = QuickAction.Capture(moduleId, ctx.getString(R.string.tasks_glance_quick), Icons.Rounded.CheckCircleOutline),
            )
        }
    }

    /** Ticked on Today: the same completion as on the Tasks screen (recurring tasks roll on). */
    override suspend fun check(row: GlanceRow) {
        if (row.key.startsWith("task:")) repo.get().complete(row.key.removePrefix("task:"))
    }

    companion object {
        const val KEY_EARLIER = "tasks:earlier"
    }
}

/** Puts this module on Today's whiteboard. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
abstract class TasksGlanceBindings {
    @dagger.Binds @dagger.multibindings.IntoSet abstract fun tasksGlanceProvider(p: TasksGlanceProvider): TodayGlanceProvider
}
