package app.klean.feature.tasks

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.klean.core.capture.CaptureActivity
import app.klean.core.common.ZoneProvider
import app.klean.core.design.component.CaptureField
import app.klean.core.design.component.KleanCard
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.feature.reminders.ReminderEditRoute
import app.klean.feature.tasks.data.TaskChangeListener
import app.klean.feature.tasks.data.TaskSource
import app.klean.feature.tasks.data.TasksRepository
import app.klean.feature.tasks.review.TaskArchiveWorker
import app.klean.feature.tasks.review.TaskReview
import app.klean.feature.tasks.ui.CheckCircle
import app.klean.feature.tasks.ui.DayTicker
import app.klean.feature.tasks.ui.TaskEditScreen
import app.klean.feature.tasks.ui.TaskFormatter
import app.klean.feature.tasks.ui.TaskRowModel
import app.klean.feature.tasks.ui.TaskText
import app.klean.feature.tasks.ui.TaskViews
import app.klean.feature.tasks.ui.TasksScreen
import app.klean.feature.tasks.ui.TasksSettingsScreen
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Tasks as the registry sees it: nav item (Tasks 10), Today card (slot TASKS), settings, capture placeholder. */
@Singleton
class TasksKleanModule @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val review: TaskReview,
    private val status: dagger.Lazy<StatusRefresh>,
) : KleanModule {
    override val id: ModuleId = ModuleId.TASKS
    override val labelRes: Int = R.string.tasks_label
    override val icon: ImageVector get() = Icons.Rounded.TaskAlt
    override val navOrder: Int = 10
    override val dashboardSlot: DashboardSlot = DashboardSlot.TASKS
    override val startRoute: Any = TasksRoute()
    override val graphRoute: Any = TasksGraph
    override val settingsRoute: Any = TasksSettingsRoute
    override val capturePlaceholderRes: Int = R.string.tasks_add

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<TasksGraph>(startDestination = TasksRoute()) {
            composable<TasksRoute>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = DEEP_LINK_TASKS },
                    navDeepLink { uriPattern = "$DEEP_LINK_TASKS/{tab}" },
                ),
            ) { entry ->
                val tab = entry.arguments?.getString("tab")
                TasksScreen(
                    initialTab = tab,
                    onOpenTask = { nav.navigate(TaskEditRoute(it)) },
                    onNewTask = { nav.navigate(TaskEditRoute()) },
                    onEditReminder = { nav.navigate(ReminderEditRoute(it)) },
                )
            }
            composable<TaskEditRoute> {
                TaskEditScreen(
                    onBack = { nav.popBackStack() },
                    onOpenTask = { nav.navigate(TaskEditRoute(it)) },
                    onCustomReminder = { id, title -> nav.navigate(ReminderEditRoute(taskId = id, title = title)) },
                )
            }
        }
        composable<TasksSettingsRoute> { TasksSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = TasksDashboardCard(onOpen, modifier)

    /** Off: the review alarm, its notification and the archive worker stop. On: they come back. */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            if (enabled) {
                review.reschedule()
                TaskArchiveWorker.enqueue(ctx)
            } else {
                review.cancel()
                TaskArchiveWorker.cancel(ctx)
            }
            status.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "tasks toggle failed", e)
        }
    }

    companion object {
        const val DEEP_LINK_TASKS = "klean://tasks"
    }
}

/** "pay rent friday" → a task due Friday in the default list. Saved at once, no AI. */
class TaskCaptureHandler @Inject constructor(
    private val repo: TasksRepository,
    private val zone: ZoneProvider,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.TASKS
    override val actionSpecs = listOf(ActionSpec(RuleCaptureClassifier.ADD_TASK, "Something to do, optionally by a date"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.ADD_TASK, 1f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        val now = input.capturedAt.toLocal(zone.current())
        // "#work" / "in my Work list" and "!"/"urgent" pick the list and priority (M28).
        val lists = repo.observeLists().first()
        return repo.add(TaskText.parse(input.text, now, source = TaskSource.CAPTURE, lists = lists))
    }

    override suspend fun undo(recordId: String) = repo.delete(recordId)
}

/**
 * Tasks on the holistic surfaces: "2 tasks" on the status line, today's tasks on the Today widget, a
 * "Tasks · 3 for today" tile and tasks per day in the week overview. Rebuilt after every task write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TaskSurfaces @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<TasksRepository>,
    private val days: DayTicker,
    private val fmt: TaskFormatter,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, TodayItemsProvider, DashboardSummaryProvider, DayActivityProvider, TaskChangeListener {
    override val moduleId: ModuleId = ModuleId.TASKS
    private val tick = MutableStateFlow(0L)

    private val todays: Flow<Pair<LocalDate, List<TaskRowModel>>> = combine(tick, days.days()) { _, d -> d }
        .flatMapLatest { d -> repo.get().observeAll().map { all -> d to TaskViews.build(all, emptyList(), d, null).today } }

    override suspend fun onTasksChanged() {
        tick.value++
        statusRefresh.get().refresh()
    }

    override fun fragments(): Flow<List<StatusFragment>> = todays.map { (_, l) ->
        if (l.isEmpty()) emptyList()
        else listOf(StatusFragment(KEY_COUNT, ctx.resources.getQuantityString(R.plurals.tasks_status_count, l.size, l.size), PRIORITY_COUNT))
    }

    override fun items(): Flow<List<TodayItem>> = todays.map { (d, l) ->
        l.take(5).map {
            val minute = it.dueMinutes
            TodayItem(
                key = "task:${it.id}",
                timeLabel = minute?.let { m -> fmt.time(d, m) },
                text = it.title,
                // Timed tasks sort by their time; all-day ones at the start of the day.
                sortKey = atMinute(d, minute ?: 0, zone.current()),
                route = TasksKleanModule.DEEP_LINK_TASKS,
            )
        }
    }

    override fun summaries(): Flow<List<SummaryItem>> = todays.map { (_, l) ->
        listOf(
            SummaryItem(
                KEY_TILE, ctx.getString(R.string.tasks_label),
                if (l.isEmpty()) ctx.getString(R.string.tasks_tile_none) else ctx.resources.getQuantityString(R.plurals.tasks_tile_value, l.size, l.size),
                Icons.Rounded.TaskAlt, TasksRoute(),
            ),
        )
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = combine(tick, repo.get().observeAll()) { _, all -> all }
        .map { all ->
            all.filter { it.parentId == null && it.dueDate != null }
                .mapNotNull { runCatching { LocalDate.parse(it.dueDate!!) }.getOrNull() }
                .filter { it in from..to }
                .groupingBy { it }.eachCount()
        }

    override fun dayRoute(date: LocalDate): Any = TasksRoute(tab = "upcoming")

    companion object {
        const val KEY_COUNT = "tasks.count"
        const val KEY_TILE = "tasks.today"
        const val PRIORITY_COUNT = 30
    }
}

data class TasksCardState(val today: LocalDate? = null, val rows: List<TaskRowModel> = emptyList(), val more: Int = 0)

/** The Today card: due today or still open from earlier days, at most 5, completed inline. */
@HiltViewModel
class TasksDashboardViewModel @Inject constructor(
    private val repo: TasksRepository,
    days: DayTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    val state: StateFlow<TasksCardState> = combine(repo.observeAll(), days.days()) { all, d ->
        val v = TaskViews.build(all, emptyList(), d, null)
        val rows = v.review + v.today
        TasksCardState(d, rows.take(MAX), (rows.size - MAX).coerceAtLeast(0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksCardState())

    fun complete(id: String) = viewModelScope.launch { repo.complete(id) }

    fun add(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds().toLocal(zone.current())
            val d = TaskText.parse(text, now, source = TaskSource.MANUAL)
            repo.add(if (d.dueDate == null) d.copy(dueDate = now.date) else d)
        }
    }

    private companion object {
        const val MAX = 5
    }
}

@Composable
fun TasksDashboardCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: TasksDashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    KleanCard(title = stringResource(R.string.tasks_card_title), onOpen = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 16.dp)) {
            if (s.rows.isEmpty()) {
                Text(
                    stringResource(R.string.tasks_card_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
            s.rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CheckCircle(false, r.title) { vm.complete(r.id) }
                    Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (s.more > 0) {
                Text(
                    pluralStringResource(R.plurals.tasks_card_more, s.more, s.more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            CaptureField(
                value = text,
                onValueChange = { text = it },
                onSubmit = {
                    vm.add(text)
                    text = ""
                },
                onMic = { ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.DASHBOARD, ModuleId.TASKS, startVoice = true)) },
                onPhoto = null,
                placeholder = stringResource(R.string.tasks_add_today),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TasksModuleBindings {
    @Binds @IntoSet abstract fun module(m: TasksKleanModule): KleanModule
    @Binds @IntoSet abstract fun capture(h: TaskCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun status(s: TaskSurfaces): StatusLineProvider
    @Binds @IntoSet abstract fun today(s: TaskSurfaces): TodayItemsProvider
    @Binds @IntoSet abstract fun summary(s: TaskSurfaces): DashboardSummaryProvider
    @Binds @IntoSet abstract fun activity(s: TaskSurfaces): DayActivityProvider
    @Binds @IntoSet abstract fun listener(s: TaskSurfaces): TaskChangeListener
}
