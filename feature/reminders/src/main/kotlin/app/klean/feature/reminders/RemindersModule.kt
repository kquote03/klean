package app.klean.feature.reminders

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SCHEDULED
import app.klean.core.data.reminder.DefaultReminderRepository.Companion.SNOOZED
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.data.reminder.ReminderRepository
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
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.feature.reminders.ui.ReminderEditScreen
import app.klean.feature.reminders.ui.ReminderText
import app.klean.feature.reminders.ui.RemindersScreen
import app.klean.feature.reminders.ui.RemindersSettingsScreen
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable object RemindersGraph
/** Reminders on their own (Tasks is where they normally live, as a tab). */
@Serializable object RemindersRoute
/** [reminderId] null adds one; [taskId] attaches the new one to a task; [title] prefills it. */
@Serializable data class ReminderEditRoute(val reminderId: String? = null, val taskId: String? = null, val title: String? = null)
@Serializable object RemindersSettingsRoute

/**
 * Reminders as the registry sees it: its own gate (so its notifications can go off alone), settings and
 * capture, but no nav item (it's a tab in Tasks, plan H5) and no Today card (Calendar's Agenda card lists
 * today's reminders). It still adds the next reminder to the status line, the Today widget and a tile.
 */
@Singleton
class RemindersKleanModule @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val scheduler: ReminderScheduler,
    private val db: KleanDatabase,
    private val status: dagger.Lazy<StatusRefresh>,
) : KleanModule {
    override val id: ModuleId = ModuleId.REMINDERS
    override val labelRes: Int = R.string.reminders_label
    override val icon: ImageVector get() = Icons.Rounded.NotificationsActive
    override val navOrder: Int = 15
    override val dashboardSlot: DashboardSlot? = null
    override val inNavigation: Boolean = false
    override val startRoute: Any = RemindersRoute
    override val graphRoute: Any = RemindersGraph
    override val settingsRoute: Any = RemindersSettingsRoute
    override val capturePlaceholderRes: Int = R.string.reminders_add_placeholder

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<RemindersGraph>(startDestination = RemindersRoute) {
            composable<RemindersRoute> {
                RemindersScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ReminderEditRoute(it)) })
            }
        }
        composable<ReminderEditRoute> { ReminderEditScreen(onBack = { nav.popBackStack() }) }
        composable<RemindersSettingsRoute> {
            RemindersSettingsScreen(onBack = { nav.popBackStack() }, onOpenReminders = { nav.navigate(RemindersRoute) })
        }
    }

    /** Off: no alarm stays armed and no reminder notification stays up. On: everything is armed again. */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            if (enabled) {
                scheduler.reschedule()
            } else {
                scheduler.cancelAll()
                val nm = NotificationManagerCompat.from(ctx)
                db.reminderDao().observeAll().first().forEach { nm.cancel(it.id, AndroidReminderNotifier.NOTIFICATION_ID) }
                ctx.stopService(android.content.Intent(ctx, app.klean.feature.reminders.alarm.AlarmRingService::class.java))
            }
            status.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "reminders toggle failed", e)
        }
    }
}

/** "remind me at 5 to call Ahmed": saved at once, no AI. No time means tomorrow 09:00. */
class ReminderCaptureHandler @Inject constructor(
    private val repo: ReminderRepository,
    private val zone: ZoneProvider,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.REMINDERS
    override val actionSpecs = listOf(ActionSpec(RuleCaptureClassifier.ADD_REMINDER, "A reminder or alarm at a time"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.ADD_REMINDER, 1f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String {
        val tz = zone.current()
        val d = ReminderText.parse(input.text, input.capturedAt, tz)
        return repo.add(d.title, d.fireAt, tz.id, d.isAlarm)
    }

    override suspend fun undo(recordId: String) = repo.delete(recordId)
}

/**
 * The next reminder on the holistic surfaces: "Call Ahmed 17:00" on the status line (a reminder that went
 * off and wasn't handled comes first), the next ones on the Today widget, a "Next reminder" tile, and
 * reminders per day in the week overview. Rebuilt after every reminder write and once a minute.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ReminderSurfaces @Inject constructor(
    private val db: dagger.Lazy<KleanDatabase>,
    private val fmt: ReminderFormatter,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, TodayItemsProvider, DashboardSummaryProvider, DayActivityProvider, ReminderChangeListener {
    override val moduleId: ModuleId = ModuleId.REMINDERS
    private val tick = MutableStateFlow(0L)
    private val minutes = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
    private val all = combine(tick, minutes) { _, _ -> }.flatMapLatest { db.get().reminderDao().observeAll() }

    override suspend fun onChanged(id: String) {
        tick.value++
        statusRefresh.get().refresh()
    }

    private fun now() = clock.now().toEpochMilliseconds()
    private fun upcoming(l: List<app.klean.core.data.db.entity.ReminderEntity>) =
        l.filter { !it.sync.deleted && (it.state == SCHEDULED || it.state == SNOOZED) }.sortedBy { ReminderTiming.dueAt(it) }

    override fun fragments(): Flow<List<StatusFragment>> = all.map { l ->
        val out = mutableListOf<StatusFragment>()
        l.filter { !it.sync.deleted && it.state == RINGING }.maxByOrNull { it.lastFiredAt ?: 0 }?.let {
            out += StatusFragment(KEY_FIRED, it.title, PRIORITY_FIRED)
        }
        val today = now().toLocal(zone.current()).date
        upcoming(l).firstOrNull { ReminderTiming.dueAt(it).toLocal(zone.current()).date == today }?.let {
            out += StatusFragment(KEY_NEXT, ctx(R.string.reminders_status_next, it.title, fmt.time(ReminderTiming.dueAt(it))), PRIORITY_NEXT)
        }
        out
    }

    override fun items(): Flow<List<TodayItem>> = all.map { l ->
        val horizon = now() + DAY_MS
        upcoming(l).filter { ReminderTiming.dueAt(it) <= horizon }.take(5).map {
            val at = ReminderTiming.dueAt(it)
            TodayItem("reminder:${it.id}", fmt.dayTime(at), it.title, at, AndroidReminderNotifier.DEEP_LINK_REMINDERS)
        }
    }

    override fun summaries(): Flow<List<SummaryItem>> = all.map { l ->
        val next = upcoming(l).firstOrNull() ?: return@map emptyList()
        listOf(
            SummaryItem(
                KEY_TILE, ctx(R.string.reminders_tile_label), fmt.dayTime(ReminderTiming.dueAt(next)),
                Icons.Rounded.NotificationsActive, RemindersRoute,
            ),
        )
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = all.map { l ->
        l.filter { !it.sync.deleted }
            .map { ReminderTiming.dueAt(it).toLocal(zone.current()).date }
            .filter { it in from..to }
            .groupingBy { it }.eachCount()
    }

    override fun dayRoute(date: LocalDate): Any = RemindersRoute

    private fun ctx(res: Int, vararg args: Any): String = fmt.string(res, *args)

    companion object {
        const val KEY_FIRED = "reminders.fired"
        const val KEY_NEXT = "reminders.next"
        const val KEY_TILE = "reminders.tile"
        const val PRIORITY_FIRED = 60
        const val PRIORITY_NEXT = 20
        private const val DAY_MS = 86_400_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModuleBindings {
    @Binds @IntoSet abstract fun module(m: RemindersKleanModule): KleanModule
    @Binds @IntoSet abstract fun capture(h: ReminderCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun status(s: ReminderSurfaces): StatusLineProvider
    @Binds @IntoSet abstract fun today(s: ReminderSurfaces): TodayItemsProvider
    @Binds @IntoSet abstract fun summary(s: ReminderSurfaces): DashboardSummaryProvider
    @Binds @IntoSet abstract fun activity(s: ReminderSurfaces): DayActivityProvider
    @Binds @IntoSet abstract fun listener(s: ReminderSurfaces): ReminderChangeListener
}
