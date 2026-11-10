package app.klean.feature.calendar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.notifications.Reschedulable
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.feature.calendar.agenda.AgendaSource
import app.klean.feature.calendar.agenda.CalendarAgenda
import app.klean.feature.calendar.agenda.DayItemsProvider
import app.klean.feature.calendar.agenda.ReminderDayItems
import app.klean.feature.calendar.agenda.TaskDueItems
import app.klean.feature.calendar.capture.CalendarCaptureHandler
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.DefaultCalendarRepository
import app.klean.feature.calendar.data.EventChangeListener
import app.klean.feature.calendar.device.AndroidDeviceCalendarQuery
import app.klean.feature.calendar.device.DeviceCalendarQuery
import app.klean.feature.calendar.device.DeviceCalendarSource
import app.klean.feature.calendar.device.DeviceInstances
import app.klean.feature.calendar.mirror.MirrorHook
import app.klean.feature.calendar.reminder.EventReminderSync
import app.klean.feature.calendar.today.AgendaCard
import app.klean.feature.calendar.today.AndroidCalendarText
import app.klean.feature.calendar.today.CalendarDashboardProviders
import app.klean.feature.calendar.today.CalendarStatusLine
import app.klean.feature.calendar.today.CalendarText
import app.klean.feature.calendar.today.CalendarTicker
import app.klean.feature.calendar.today.MinuteCalendarTicker
import app.klean.feature.calendar.ui.CalendarDeviceAccess
import app.klean.feature.calendar.ui.CalendarPrefs
import app.klean.feature.calendar.ui.CalendarScreen
import app.klean.feature.calendar.ui.DeviceAccess
import app.klean.feature.calendar.ui.SettingsCalendarPrefs
import app.klean.feature.calendar.ui.edit.EventEditScreen
import app.klean.feature.calendar.ui.settings.AndroidIcsFiles
import app.klean.feature.calendar.ui.settings.CalendarSettingsScreen
import app.klean.feature.calendar.ui.settings.IcsFiles
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

/** The calendar as the registry sees it: nav item, routes, the Agenda card on Today, settings entry. */
@Singleton
class CalendarKleanModule @Inject constructor(
    private val mirror: MirrorHook,
    private val reminders: EventReminderSync,
    private val status: CalendarStatusLine,
) : KleanModule {
    override val id: ModuleId = ModuleId.CALENDAR
    override val labelRes: Int = R.string.calendar_label
    override val icon: ImageVector get() = Icons.Rounded.CalendarMonth
    override val navOrder: Int = 20
    override val dashboardSlot: DashboardSlot = DashboardSlot.AGENDA
    override val startRoute: Any = CalendarRoute
    override val graphRoute: Any = CalendarGraph
    override val settingsRoute: Any = CalendarSettingsRoute
    override val capturePlaceholderRes: Int = R.string.calendar_capture_placeholder

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<CalendarGraph>(startDestination = CalendarRoute) {
            composable<CalendarRoute>(deepLinks = listOf(navDeepLink { uriPattern = CalendarRoutes.DEEP_LINK })) {
                CalendarScreen(
                    onOpenEvent = { id, at -> nav.navigate(EventEditRoute(eventId = id, occurrenceStart = at)) },
                    onNewEvent = { day -> nav.navigate(EventEditRoute(isoDate = day?.toString())) },
                    onOpenSettings = { nav.navigate(CalendarSettingsRoute) },
                    onOpenLink = { link -> runCatching { nav.navigate(android.net.Uri.parse(link)) } },
                )
            }
            composable<CalendarDayRoute>(deepLinks = listOf(navDeepLink { uriPattern = CalendarRoutes.DEEP_LINK_DAY })) {
                CalendarScreen(
                    onOpenEvent = { id, at -> nav.navigate(EventEditRoute(eventId = id, occurrenceStart = at)) },
                    onNewEvent = { day -> nav.navigate(EventEditRoute(isoDate = day?.toString())) },
                    onOpenSettings = { nav.navigate(CalendarSettingsRoute) },
                    onOpenLink = { link -> runCatching { nav.navigate(android.net.Uri.parse(link)) } },
                )
            }
            composable<EventEditRoute> { EventEditScreen(onDone = { nav.popBackStack() }) }
        }
        // Reached from Settings, so it stays outside the calendar graph (the bar keeps Settings selected).
        composable<CalendarSettingsRoute> { CalendarSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = AgendaCard(onOpen, modifier)

    /** Off: the provider calendar and event reminders go; on: both come back. Then the surfaces follow. */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        runCatching { mirror.reschedule() }
        runCatching { reminders.reschedule() }
        status.refresh()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarBindings {
    @Binds @IntoSet abstract fun module(m: CalendarKleanModule): KleanModule
    @Binds abstract fun repository(impl: DefaultCalendarRepository): CalendarRepository
    @Binds abstract fun deviceQuery(impl: AndroidDeviceCalendarQuery): DeviceCalendarQuery
    @Binds abstract fun deviceInstances(impl: DeviceCalendarSource): DeviceInstances
    @Binds abstract fun deviceAccess(impl: CalendarDeviceAccess): DeviceAccess
    @Binds abstract fun prefs(impl: SettingsCalendarPrefs): CalendarPrefs
    @Binds abstract fun icsFiles(impl: AndroidIcsFiles): IcsFiles
    @Binds abstract fun text(impl: AndroidCalendarText): CalendarText
    @Binds abstract fun ticker(impl: MinuteCalendarTicker): CalendarTicker

    @Binds @IntoSet abstract fun capture(h: CalendarCaptureHandler): CaptureHandler

    @Multibinds abstract fun eventListeners(): Set<EventChangeListener>
    @Binds @IntoSet abstract fun mirrorListener(h: MirrorHook): EventChangeListener
    @Binds @IntoSet abstract fun mirrorResync(h: MirrorHook): Reschedulable
    @Binds @IntoSet abstract fun reminderListener(s: EventReminderSync): EventChangeListener
    @Binds @IntoSet abstract fun reminderChanges(s: EventReminderSync): ReminderChangeListener
    @Binds @IntoSet abstract fun reminderResync(s: EventReminderSync): Reschedulable
    @Binds @IntoSet abstract fun statusListener(s: CalendarStatusLine): EventChangeListener
    @Binds @IntoSet abstract fun statusLine(s: CalendarStatusLine): StatusLineProvider

    @Binds @IntoSet abstract fun taskItems(p: TaskDueItems): DayItemsProvider
    @Binds @IntoSet abstract fun reminderItems(p: ReminderDayItems): DayItemsProvider

    companion object {
        @Provides @Singleton @JvmStatic
        fun agenda(
            repo: CalendarRepository,
            device: DeviceInstances,
            providers: Set<@JvmSuppressWildcards DayItemsProvider>,
            gate: ModuleGate,
            prefs: CalendarPrefs,
            zone: ZoneProvider,
        ): AgendaSource = CalendarAgenda(repo, device, providers, gate, { prefs.showTasks }, { prefs.showReminders }, zone)

        @Provides @Singleton @JvmStatic
        fun dashboard(
            agenda: AgendaSource,
            clock: kotlin.time.Clock,
            zone: ZoneProvider,
            text: CalendarText,
            ticker: CalendarTicker,
        ): CalendarDashboardProviders = CalendarDashboardProviders(agenda, clock, zone, text, ticker)

        @Provides @IntoSet @JvmStatic
        fun summaries(p: CalendarDashboardProviders): DashboardSummaryProvider = p

        @Provides @IntoSet @JvmStatic
        fun activity(p: CalendarDashboardProviders): DayActivityProvider = p

        @Provides @IntoSet @JvmStatic
        fun todayItems(p: CalendarDashboardProviders): TodayItemsProvider = p
    }
}
