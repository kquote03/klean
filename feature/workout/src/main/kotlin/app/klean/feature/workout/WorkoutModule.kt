package app.klean.feature.workout

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.klean.core.design.component.EntryRow
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
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.data.WorkoutChangeListener
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.feature.workout.data.WorkoutSource
import app.klean.feature.workout.suggest.WorkoutSuggestion
import app.klean.feature.workout.ui.ExerciseHistoryScreen
import app.klean.feature.workout.ui.ExercisesScreen
import app.klean.feature.workout.ui.SessionScreen
import app.klean.feature.workout.ui.TemplateEditScreen
import app.klean.feature.workout.ui.WorkoutFormatter
import app.klean.feature.workout.ui.WorkoutScreen
import app.klean.feature.workout.ui.WorkoutSettingsScreen
import app.klean.feature.workout.ui.WorkoutTicker
import app.klean.feature.workout.ui.rememberFormatter
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Starts a session (from a notification action) and opens it. [templateId] null = an empty one. */
@Serializable data class WorkoutStartRoute(val templateId: String? = null)

/** Workout as the registry sees it: in "More" (navOrder 40), Today card (slot WORKOUT), settings, capture. */
@Singleton
class WorkoutKleanModule @Inject constructor(
    private val suggestion: WorkoutSuggestion,
    private val status: dagger.Lazy<StatusRefresh>,
) : KleanModule {
    override val id: ModuleId = ModuleId.WORKOUT
    override val labelRes: Int = R.string.workout_label
    override val icon get() = Icons.Rounded.FitnessCenter
    override val navOrder: Int = 40
    override val dashboardSlot: DashboardSlot = DashboardSlot.WORKOUT
    override val startRoute: Any = WorkoutRoute
    override val graphRoute: Any = WorkoutGraph
    override val settingsRoute: Any = WorkoutSettingsRoute
    override val capturePlaceholderRes: Int = R.string.workout_capture_placeholder

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<WorkoutGraph>(startDestination = WorkoutRoute) {
            composable<WorkoutRoute>(deepLinks = listOf(navDeepLink { uriPattern = DEEP_LINK })) {
                WorkoutScreen(
                    onOpenSession = { nav.navigate(SessionRoute(it)) },
                    onEditTemplate = { nav.navigate(TemplateEditRoute(it)) },
                    onExercises = { nav.navigate(ExercisesRoute) },
                    onSettings = { nav.navigate(WorkoutSettingsRoute) },
                )
            }
            composable<TemplateEditRoute> { TemplateEditScreen(onBack = { nav.popBackStack() }) }
            composable<SessionRoute> { SessionScreen(onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack() }) }
            composable<ExercisesRoute> { ExercisesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(ExerciseHistoryRoute(it)) }) }
            composable<ExerciseHistoryRoute> { ExerciseHistoryScreen(onBack = { nav.popBackStack() }) }
            composable<WorkoutStartRoute>(
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK/start" }, navDeepLink { uriPattern = "$DEEP_LINK/start/{templateId}" }),
            ) { entry ->
                val vm: WorkoutStartViewModel = hiltViewModel()
                val template = entry.arguments?.getString("templateId")
                LaunchedEffect(Unit) {
                    val id = vm.start(template)
                    nav.navigate(SessionRoute(id)) { popUpTo<WorkoutStartRoute> { inclusive = true } }
                }
            }
        }
        composable<WorkoutSettingsRoute> { WorkoutSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = WorkoutDashboardCard(onOpen, modifier)

    /** Off: the suggestion alarm and its notification stop. On: the alarm comes back (if the setting is on). */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            if (enabled) suggestion.reschedule() else suggestion.cancel()
            status.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "workout toggle failed", e)
        }
    }

    companion object {
        const val DEEP_LINK = "klean://workout"
        fun startLink(templateId: String?): String = if (templateId == null) "$DEEP_LINK/start" else "$DEEP_LINK/start/$templateId"
    }
}

@HiltViewModel
class WorkoutStartViewModel @Inject constructor(private val repo: WorkoutRepository) : ViewModel() {
    suspend fun start(templateId: String?): String = repo.start(templateId)
}

/** "did 30 pushups", "ran 5k" → a finished session, saved at once with no AI. */
class WorkoutCaptureHandler @Inject constructor(private val repo: WorkoutRepository) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.WORKOUT
    override val actionSpecs = listOf(ActionSpec(RuleCaptureClassifier.LOG_WORKOUT, "A workout that was done, e.g. 30 pushups or a 5 km run"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.LOG_WORKOUT, 1f)
    override suspend fun execute(input: CaptureInput, action: CandidateAction): String = repo.logText(input.text, input.capturedAt, WorkoutSource.CAPTURE)
    override suspend fun undo(recordId: String) = repo.delete(recordId)
}

/**
 * Workout on the holistic surfaces: "Workout in progress" on the status line and the Today widget while a
 * session runs, a "Last workout · Sat" tile and sessions per day in the week overview.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WorkoutSurfaces @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<WorkoutRepository>,
    private val fmt: WorkoutFormatter,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, TodayItemsProvider, DashboardSummaryProvider, DayActivityProvider, WorkoutChangeListener {
    override val moduleId: ModuleId = ModuleId.WORKOUT
    private val tick = MutableStateFlow(0L)
    private val sessions: Flow<List<SessionWithSets>> = tick.flatMapLatest { repo.get().observeSessions() }
    private fun today() = clock.now().toEpochMilliseconds().toLocal(zone.current()).date

    override suspend fun onWorkoutsChanged() {
        tick.value++
        statusRefresh.get().refresh()
    }

    override fun fragments(): Flow<List<StatusFragment>> = sessions.map { l ->
        when {
            l.any { it.isOpen } -> listOf(StatusFragment(KEY_STATUS, ctx.getString(R.string.workout_status_running), PRIORITY_RUNNING))
            l.any { fmt.localDate(it.session.startedAt) == today() } -> listOf(StatusFragment(KEY_STATUS, ctx.getString(R.string.workout_status_today), PRIORITY_TODAY))
            else -> emptyList()
        }
    }

    override fun items(): Flow<List<TodayItem>> = sessions.map { l ->
        l.filter { it.isOpen }.map {
            TodayItem("workout:${it.session.id}", fmt.time(it.session.startedAt), ctx.getString(R.string.workout_today_item), it.session.startedAt, WorkoutKleanModule.DEEP_LINK)
        }
    }

    override fun summaries(): Flow<List<SummaryItem>> = sessions.map { l ->
        val last = l.firstOrNull()
        val value = if (last == null) ctx.getString(R.string.workout_tile_none) else ctx.getString(R.string.workout_tile_last, fmt.day(last.session.startedAt, today()))
        listOf(SummaryItem(KEY_TILE, ctx.getString(R.string.workout_label), value, Icons.Rounded.FitnessCenter, WorkoutRoute))
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = sessions.map { l ->
        l.map { fmt.localDate(it.session.startedAt) }.filter { it in from..to }.groupingBy { it }.eachCount()
    }

    override fun dayRoute(date: LocalDate): Any = WorkoutRoute

    companion object {
        const val KEY_STATUS = "workout.status"
        const val KEY_TILE = "workout.last"
        const val PRIORITY_RUNNING = 45
        const val PRIORITY_TODAY = 5
    }
}

data class WorkoutCardState(val today: LocalDate? = null, val open: SessionWithSets? = null, val recent: List<SessionWithSets> = emptyList(), val firstTemplate: Pair<String, String>? = null)

/** The Today card: the running session or a Start button, the last 3 sessions, and a one-line log field. */
@HiltViewModel
class WorkoutDashboardViewModel @Inject constructor(
    private val repo: WorkoutRepository,
    ticker: WorkoutTicker,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    val state: StateFlow<WorkoutCardState> = combine(repo.observeSessions(), repo.observeTemplates(), ticker.ticks(60_000)) { s, t, now ->
        WorkoutCardState(
            now.toLocal(zone.current()).date, s.firstOrNull { it.isOpen }, s.filter { !it.isOpen }.take(MAX),
            t.firstOrNull()?.let { it.id to it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutCardState())

    fun start(templateId: String?, then: (String) -> Unit) = viewModelScope.launch { then(repo.start(templateId)) }

    fun log(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.logText(text, clock.now().toEpochMilliseconds()) }
    }

    private companion object {
        const val MAX = 3
    }
}

@Composable
fun WorkoutDashboardCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: WorkoutDashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberFormatter()
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    KleanCard(title = stringResource(R.string.workout_card_title), onOpen = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 16.dp)) {
            val open = s.open
            if (open != null) {
                Text(
                    stringResource(R.string.workout_in_progress, fmt.time(open.session.startedAt)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp),
                )
                FilledTonalButton(onClick = onOpen, modifier = Modifier.padding(start = 12.dp, top = 4.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.workout_continue))
                }
            } else {
                val first = s.firstTemplate
                FilledTonalButton(
                    onClick = { vm.start(first?.first) { onOpen() } },
                    modifier = Modifier.padding(start = 12.dp).heightIn(min = 48.dp),
                ) {
                    Text(
                        if (first != null) stringResource(R.string.workout_start_named, first.second) else stringResource(R.string.workout_start_empty),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val today = s.today
            s.recent.forEach { r ->
                EntryRow(time = if (today != null) fmt.day(r.session.startedAt, today) else "", text = fmt.names(r), meta = {
                    Text(fmt.summary(r), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }, onClick = onOpen)
            }
            CaptureField(
                value = text,
                onValueChange = { text = it },
                onSubmit = {
                    vm.log(text)
                    text = ""
                },
                onMic = { ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.DASHBOARD, ModuleId.WORKOUT, startVoice = true)) },
                onPhoto = null,
                placeholder = stringResource(R.string.workout_log_placeholder),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )
        }
    }
}

/** Health Connect for workouts (M22: [app.klean.feature.workout.health.HealthConnectWorkoutBridge]). */
interface HealthConnectBridge {
    /** Writes a finished session to Health Connect (best-effort; nothing without access). */
    suspend fun writeSession(sessionId: String) {}
    /** Brings in other apps' sessions of the last [days] days; returns how many were added. */
    suspend fun importRecent(days: Int): Int = 0
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModuleBindings {
    @Binds @IntoSet abstract fun module(m: WorkoutKleanModule): KleanModule
    @Binds @IntoSet abstract fun capture(h: WorkoutCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun status(s: WorkoutSurfaces): StatusLineProvider
    @Binds @IntoSet abstract fun today(s: WorkoutSurfaces): TodayItemsProvider
    @Binds @IntoSet abstract fun summary(s: WorkoutSurfaces): DashboardSummaryProvider
    @Binds @IntoSet abstract fun activity(s: WorkoutSurfaces): DayActivityProvider
    @Binds @IntoSet abstract fun listener(s: WorkoutSurfaces): WorkoutChangeListener
    @Binds abstract fun healthConnect(b: app.klean.feature.workout.health.HealthConnectWorkoutBridge): HealthConnectBridge
    @Binds abstract fun locationFeed(f: app.klean.feature.workout.jog.AndroidLocationFeed): app.klean.feature.workout.jog.LocationFeed
}
