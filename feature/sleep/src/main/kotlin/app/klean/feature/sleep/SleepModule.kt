package app.klean.feature.sleep

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.sleep.SleepScheduleRepository
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
import app.klean.feature.sleep.data.SleepChangeListener
import app.klean.feature.sleep.data.SleepCommand
import app.klean.feature.sleep.data.SleepMath
import app.klean.feature.sleep.data.SleepRepository
import app.klean.feature.sleep.data.SleepSource
import app.klean.feature.sleep.data.SleepText
import app.klean.feature.sleep.notify.BedtimeReminder
import app.klean.feature.sleep.notify.SleepSessionNotifier
import app.klean.feature.sleep.ui.SleepEditScreen
import app.klean.feature.sleep.ui.SleepFormatter
import app.klean.feature.sleep.ui.SleepScreen
import app.klean.feature.sleep.ui.SleepSettingsScreen
import app.klean.feature.sleep.ui.SleepTicker
import app.klean.feature.sleep.ui.SleepToggleButton
import app.klean.feature.sleep.ui.SleepToggleViewModel
import app.klean.feature.sleep.ui.SleepViewModel
import app.klean.feature.sleep.ui.rememberSleepFormatter
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.DayWindows
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

@Serializable object SleepGraph
@Serializable object SleepRoute
/** [sessionId] null adds a night. */
@Serializable data class SleepEditRoute(val sessionId: String? = null)
@Serializable object SleepSettingsRoute
/** One tap from the Today widget (deep link `klean://sleep/toggle`): toggles, then shows Sleep. */
@Serializable object SleepToggleRoute

/** Sleep as the registry sees it: in "More" (navOrder 50), Today card (slot SLEEP), settings, capture. */
@Singleton
class SleepKleanModule @Inject constructor(
    private val bedtime: BedtimeReminder,
    private val session: SleepSessionNotifier,
    private val status: dagger.Lazy<StatusRefresh>,
) : KleanModule {
    override val id: ModuleId = ModuleId.SLEEP
    override val labelRes: Int = R.string.sleep_label
    override val icon get() = Icons.Rounded.Bedtime
    override val navOrder: Int = 50
    override val dashboardSlot: DashboardSlot = DashboardSlot.SLEEP
    override val startRoute: Any = SleepRoute
    override val graphRoute: Any = SleepGraph
    override val settingsRoute: Any = SleepSettingsRoute
    override val capturePlaceholderRes: Int = R.string.sleep_capture_placeholder

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<SleepGraph>(startDestination = SleepRoute) {
            composable<SleepRoute>(deepLinks = listOf(navDeepLink { uriPattern = DEEP_LINK })) {
                SleepScreen(onEdit = { nav.navigate(SleepEditRoute(it)) }, onSettings = { nav.navigate(SleepSettingsRoute) })
            }
            composable<SleepEditRoute> { SleepEditScreen(onBack = { nav.popBackStack() }) }
            composable<SleepToggleRoute>(deepLinks = listOf(navDeepLink { uriPattern = DEEP_LINK_TOGGLE })) {
                val vm: SleepToggleViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    vm.toggle()
                    nav.navigate(SleepRoute) { popUpTo<SleepToggleRoute> { inclusive = true } }
                }
            }
        }
        composable<SleepSettingsRoute> { SleepSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = SleepDashboardCard(onOpen, modifier)

    /**
     * Off: the bedtime reminder and the while-asleep notification go. Quiet hours stay: the schedule is
     * their source with Sleep on or off. On: both come back.
     */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            if (enabled) {
                bedtime.reschedule()
                session.update()
            } else {
                bedtime.cancel()
                session.cancel()
            }
            status.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "sleep toggle failed", e)
        }
    }

    companion object {
        const val DEEP_LINK = "klean://sleep"
        const val DEEP_LINK_TOGGLE = "klean://sleep/toggle"
    }
}

/** "slept at 11 woke at 7", "going to bed", "woke up". Saved at once, no AI. */
class SleepCaptureHandler @Inject constructor(
    private val repo: SleepRepository,
    private val schedule: SleepScheduleRepository,
    private val zone: ZoneProvider,
) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.SLEEP
    override val actionSpecs = listOf(ActionSpec(RuleCaptureClassifier.LOG_SLEEP, "Going to bed, waking up, or a night's sleep with times"))
    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, RuleCaptureClassifier.LOG_SLEEP, 1f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String =
        when (val c = SleepText.parse(input.text, input.capturedAt, zone.current(), schedule.current())) {
            is SleepCommand.GoToBed -> repo.goToBed(c.at, SleepSource.CAPTURE)
            is SleepCommand.WakeUp -> repo.wakeUp(c.at, SleepSource.CAPTURE)
            is SleepCommand.Night -> repo.log(c.bedAt, c.wakeAt, SleepSource.CAPTURE)
        }

    /** Undo: a night that was only closed by this capture opens again; otherwise it goes. */
    override suspend fun undo(recordId: String) {
        val s = repo.get(recordId) ?: return
        if (s.source != SleepSource.CAPTURE && s.wakeAt != null) repo.update(s.id, s.bedAt, null) else repo.delete(recordId)
    }
}

/** What the one-tap surfaces should offer at [now]. Pure. */
object SleepOffer {
    /** "Going to bed" is offered from 2 h before bedtime until wake time, while no night is open. */
    fun offerBedtime(now: Long, q: QuietHours, zone: kotlinx.datetime.TimeZone): Long? {
        if (q.isDisabled) return null
        val date = now.toLocal(zone).date
        val len = ((q.endMinute - q.startMinute + 1440) % 1440) * MINUTE_MS
        return listOf(date.minus(DatePeriod(days = 1)), date, date.plus(DatePeriod(days = 1)))
            .map { atMinute(it, q.startMinute, zone) }
            .firstOrNull { start -> now >= start - 2 * HOUR_MS && now < start + len }
    }
}

/**
 * Sleep on the holistic surfaces: "Sleeping since 23:40" on the status line, a one-tap "Going to bed" /
 * "I'm up" item on the Today widget, a "Last night · 7 h 20 m" tile, and nights in the week overview.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SleepSurfaces @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<SleepRepository>,
    private val schedule: dagger.Lazy<SleepScheduleRepository>,
    private val fmt: SleepFormatter,
    private val ticker: SleepTicker,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, TodayItemsProvider, DashboardSummaryProvider, DayActivityProvider, SleepChangeListener {
    override val moduleId: ModuleId = ModuleId.SLEEP
    private val tick = MutableStateFlow(0L)

    private data class Snapshot(val now: Long, val q: QuietHours, val sessions: List<SleepSessionEntity>) {
        val open get() = sessions.firstOrNull { it.wakeAt == null }
    }

    private val snapshot: Flow<Snapshot> = tick.flatMapLatest {
        combine(ticker.ticks(), schedule.get().quietHours, repo.get().observeSessions()) { now, q, s -> Snapshot(now, q, s) }
    }

    override suspend fun onSleepChanged() {
        tick.value++
        statusRefresh.get().refresh()
    }

    private fun lastNight(s: Snapshot): SleepSessionEntity? =
        SleepMath.lastNight(s.sessions, DayWindows.logicalDate(s.now, s.q, zone.current()), s.q, zone.current())

    override fun fragments(): Flow<List<StatusFragment>> = snapshot.map { s ->
        val open = s.open
        val last = lastNight(s)
        when {
            open != null -> listOf(StatusFragment(KEY_STATUS, ctx.getString(R.string.sleep_since, fmt.time(open.bedAt)), PRIORITY_ASLEEP))
            last != null -> listOf(StatusFragment(KEY_STATUS, ctx.getString(R.string.sleep_status_slept, fmt.duration(last.wakeAt!! - last.bedAt)), PRIORITY_SLEPT))
            else -> emptyList()
        }
    }

    override fun items(): Flow<List<TodayItem>> = snapshot.map { s ->
        val open = s.open
        if (open != null) {
            listOf(TodayItem("sleep:open", fmt.time(open.bedAt), ctx.getString(R.string.sleep_item_asleep), open.bedAt, SleepKleanModule.DEEP_LINK_TOGGLE))
        } else {
            val bed = SleepOffer.offerBedtime(s.now, s.q, zone.current()) ?: return@map emptyList()
            listOf(TodayItem("sleep:bed", fmt.time(bed), ctx.getString(R.string.sleep_item_bedtime), bed, SleepKleanModule.DEEP_LINK_TOGGLE))
        }
    }

    override fun summaries(): Flow<List<SummaryItem>> = snapshot.map { s ->
        val open = s.open
        val last = lastNight(s)
        val value = when {
            open != null -> ctx.getString(R.string.sleep_since, fmt.time(open.bedAt))
            last != null -> ctx.getString(R.string.sleep_tile_last, fmt.duration(last.wakeAt!! - last.bedAt))
            else -> ctx.getString(R.string.sleep_tile_none)
        }
        listOf(SummaryItem(KEY_TILE, ctx.getString(R.string.sleep_label), value, Icons.Rounded.Bedtime, SleepRoute))
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = snapshot.map { s ->
        s.sessions.mapNotNull { SleepMath.wakeDay(it, s.q, zone.current()) }.filter { it in from..to }.groupingBy { it }.eachCount()
    }

    override fun dayRoute(date: LocalDate): Any = SleepRoute

    companion object {
        const val KEY_STATUS = "sleep.status"
        const val KEY_TILE = "sleep.lastNight"
        const val PRIORITY_ASLEEP = 50
        const val PRIORITY_SLEPT = 4
    }
}

/** The Today card: last night (or "Sleeping since"), and the one-tap button. */
@Composable
fun SleepDashboardCard(onOpen: () -> Unit, modifier: Modifier = Modifier, vm: SleepViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fmt = rememberSleepFormatter()
    KleanCard(title = stringResource(R.string.sleep_card_title), onOpen = onOpen, modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val open = s.open
            val last = s.lastNight
            Text(
                when {
                    open != null -> stringResource(R.string.sleep_since, fmt.time(open.bedAt))
                    last != null -> stringResource(R.string.sleep_last_night, fmt.duration(last.wakeAt!! - last.bedAt)) + " · " + fmt.range(last)
                    else -> stringResource(R.string.sleep_card_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (open == null && last == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (s.loaded) SleepToggleButton(open != null, vm::toggle, Modifier.padding(top = 8.dp))
        }
    }
}

/** Health Connect sleep sessions come in through this (M22: [app.klean.feature.sleep.data.HealthConnectSleepImport]). */
interface SleepImportSource {
    suspend fun importRecent(days: Int): Int = 0
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepModuleBindings {
    @Binds @IntoSet abstract fun module(m: SleepKleanModule): KleanModule
    @Binds @IntoSet abstract fun capture(h: SleepCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun status(s: SleepSurfaces): StatusLineProvider
    @Binds @IntoSet abstract fun today(s: SleepSurfaces): TodayItemsProvider
    @Binds @IntoSet abstract fun summary(s: SleepSurfaces): DashboardSummaryProvider
    @Binds @IntoSet abstract fun activity(s: SleepSurfaces): DayActivityProvider
    @Binds @IntoSet abstract fun listener(s: SleepSurfaces): SleepChangeListener
    @Binds abstract fun importSource(s: app.klean.feature.sleep.data.HealthConnectSleepImport): SleepImportSource
}
