package app.klean.watch

import android.content.Context
import android.util.Log
import app.klean.R
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusSurface
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.reminders.ReminderTiming
import app.klean.feature.tasks.data.TasksRepository
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.shared.module.ModuleId
import app.klean.shared.wear.ActiveWorkout
import app.klean.shared.wear.FoodLine
import app.klean.shared.wear.NextUp
import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WorkoutRef
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Reads what the watch shows, from the phone's own data. `generatedAt` is left for the publisher. */
fun interface WatchSnapshotSource {
    suspend fun snapshot(): WatchState
}

/**
 * Keeps the watch's `/klean/state` DataItem current (M27). It is a [StatusSurface], so every module's
 * existing `StatusRefresh` call after a write (diet, tasks, reminders, workouts, …) reaches it, and it
 * also follows module toggles. Requests are debounced ([DEBOUNCE_MS]) and an unchanged snapshot isn't
 * put again. With no Data Layer on this phone it never reads anything.
 */
@OptIn(FlowPreview::class)
class WatchStatePublisher(
    private val source: WatchSnapshotSource,
    private val layer: WearDataLayer,
    private val gate: ModuleGate,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
) : StatusSurface {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutex = Mutex()
    private var started = false
    private var last: WatchState? = null

    override suspend fun refresh() = request()

    fun request() {
        if (started) requests.tryEmit(Unit)
    }

    /** Called once at app start. The gate's first value doubles as the initial publish. */
    fun start() {
        if (!layer.available || started) return
        started = true
        scope.launch {
            try {
                merge(requests, gate.disabledIds.distinctUntilChanged().map { })
                    .debounce(debounceMs)
                    .collect { runCatching { publishNow() }.onFailure { Log.w("Klean", "watch state publish failed", it) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "watch state publisher stopped", e)
            }
        }
    }

    /** Puts the snapshot unless it's the one the watch already has. True when something was put. */
    suspend fun publishNow(): Boolean = mutex.withLock {
        val s = source.snapshot().copy(generatedAt = 0)
        if (s == last) return@withLock false
        val ok = layer.putState(WearCodec.encodeState(s.copy(generatedAt = clock.now().toEpochMilliseconds())))
        if (ok) last = s
        ok
    }

    companion object {
        const val DEBOUNCE_MS = 2_000L
    }
}

/** Pure pieces of the snapshot, kept apart so they're tested without a database. */
object WatchSnapshots {
    const val MAX_RECENT = 5

    /**
     * The next reminder or task: the soonest timed one still ahead (reminders at their snooze time,
     * tasks due today at their time), else the first untimed open task for today.
     */
    fun nextUp(tasks: List<TaskEntity>, reminders: List<ReminderEntity>, today: LocalDate, nowMs: Long, zone: TimeZone): NextUp? {
        val todays = tasks.filter {
            !it.sync.deleted && it.completedAt == null && it.status == "OPEN" && it.parentId == null && it.archivedAt == null &&
                it.dueDate == today.toString()
        }
        val timed = buildList {
            reminders.filter { !it.sync.deleted && (it.state == "SCHEDULED" || it.state == "SNOOZED") }
                .forEach { r -> ReminderTiming.dueAt(r).takeIf { it >= nowMs }?.let { add(NextUp("REMINDER", r.title, it)) } }
            todays.forEach { t ->
                t.dueMinutes?.let { m -> LocalDateTime(today, LocalTime(m / 60, m % 60)).toInstant(zone).toEpochMilliseconds() }
                    ?.takeIf { it >= nowMs }?.let { add(NextUp("TASK", t.title, it)) }
            }
        }
        return timed.minByOrNull { it.at!! }
            ?: todays.filter { it.dueMinutes == null }.sortedWith(compareByDescending<TaskEntity> { it.priority }.thenBy { it.position })
                .firstOrNull()?.let { NextUp("TASK", it.title, null) }
    }

    fun workouts(templates: List<WorkoutTemplateEntity>): List<WorkoutRef> =
        templates.filterNot { it.sync.deleted }.sortedBy { it.position }.map { WorkoutRef(it.id, it.name) }

    fun active(open: SessionWithSets?, templates: List<WorkoutTemplateEntity>, fallbackName: String): ActiveWorkout? {
        if (open == null || !open.isOpen || open.session.sync.deleted) return null
        val name = templates.firstOrNull { it.id == open.session.templateId }?.name ?: fallbackName
        return ActiveWorkout(open.session.id, name, open.sets.count { !it.sync.deleted })
    }
}

/** [WatchSnapshotSource] over the module repositories. A switched-off module contributes nothing. */
class RepoWatchSnapshotSource @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val gate: ModuleGate,
    private val registry: ModuleRegistry,
    private val diet: dagger.Lazy<DietRepository>,
    private val tasks: dagger.Lazy<TasksRepository>,
    private val reminders: dagger.Lazy<ReminderRepository>,
    private val workouts: dagger.Lazy<WorkoutRepository>,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : WatchSnapshotSource {
    override suspend fun snapshot(): WatchState {
        val off = gate.disabledIds.first()
        fun on(id: ModuleId) = id !in off
        val now = clock.now().toEpochMilliseconds()
        val z = zone.current()
        val dietOn = on(ModuleId.DIET)
        val food = if (!dietOn) emptyList() else diet.get().today().sortedBy { it.eatenAt }.map { FoodLine(it.eatenAt, it.rawText) }
        val recent = if (!dietOn) emptyList() else diet.get().quickRepeats().take(WatchSnapshots.MAX_RECENT)
        val taskList = if (on(ModuleId.TASKS)) tasks.get().observeAll().first() else emptyList()
        val reminderList = if (on(ModuleId.REMINDERS)) reminders.get().observeUpcoming(now).first() else emptyList()
        val today = tasks.get().today()
        val workoutOn = on(ModuleId.WORKOUT)
        val templates = if (workoutOn) workouts.get().observeTemplates().first() else emptyList()
        val open = if (workoutOn) workouts.get().openSession()?.let { workouts.get().session(it.id) } else null
        return WatchState(
            todayFood = food,
            recentFoods = recent,
            next = WatchSnapshots.nextUp(taskList, reminderList, today, now, z),
            workouts = WatchSnapshots.workouts(templates),
            active = WatchSnapshots.active(open, templates, ctx.getString(R.string.watch_workout_session)),
            dietOn = dietOn,
            modules = registry.all.map { it.id }.filter { on(it) }.map { it.value }.sorted(),
        )
    }
}

/** The app's single publisher, wired to the real repositories and the app scope. */
@Singleton
class AppWatchStatePublisher @Inject constructor(
    source: RepoWatchSnapshotSource,
    layer: WearDataLayer,
    gate: ModuleGate,
    clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) : StatusSurface {
    val publisher = WatchStatePublisher(source, layer, gate, clock, scope)
    override suspend fun refresh() = publisher.refresh()
    fun start() = publisher.start()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchStateModule {
    @Binds @IntoSet abstract fun surface(p: AppWatchStatePublisher): StatusSurface
}
