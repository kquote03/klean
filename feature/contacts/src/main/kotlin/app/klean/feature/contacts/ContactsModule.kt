package app.klean.feature.contacts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.klean.core.data.reminder.ReminderChangeListener
import app.klean.core.notifications.Reschedulable
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.DayItemsProvider
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.OnboardingStepProvider
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.feature.contacts.birthday.AndroidBirthdayText
import app.klean.feature.contacts.birthday.BirthdayReminderSync
import app.klean.feature.contacts.birthday.BirthdayText
import app.klean.feature.contacts.capture.ContactsCaptureHandler
import app.klean.feature.contacts.data.ContactChangeListener
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.device.AndroidDeviceContacts
import app.klean.feature.contacts.device.DeviceContacts
import app.klean.feature.contacts.mirror.ContactsMirrorHook
import app.klean.feature.contacts.mirror.ProviderJoin
import app.klean.feature.contacts.today.AndroidBirthdaySurfaceText
import app.klean.feature.contacts.today.BirthdaySurfaceText
import app.klean.feature.contacts.today.BirthdaySurfaces
import app.klean.feature.contacts.today.BirthdaysCard
import app.klean.feature.contacts.ui.AndroidVcfFiles
import app.klean.feature.contacts.ui.BirthdaysSettingsScreen
import app.klean.feature.contacts.ui.ContactDetailScreen
import app.klean.feature.contacts.ui.ContactEditScreen
import app.klean.feature.contacts.ui.ContactsOnboardingStep
import app.klean.feature.contacts.ui.ContactsScreen
import app.klean.feature.contacts.ui.ContactsSettingsScreen
import app.klean.feature.contacts.ui.VcfFiles
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Contacts as the registry sees it: the "Contacts" nav item (navOrder 30) with a Birthdays tab, settings, capture.
 * No Today card of its own (Birthdays has one).
 */
@Singleton
class ContactsKleanModule @Inject constructor(
    private val mirror: ContactsMirrorHook,
    private val status: dagger.Lazy<StatusRefresh>,
) : KleanModule {
    override val id: ModuleId = ModuleId.CONTACTS
    override val labelRes: Int = R.string.contacts_label
    override val icon: ImageVector get() = Icons.Rounded.Contacts
    override val navOrder: Int = 30
    override val dashboardSlot: DashboardSlot? = null
    override val startRoute: Any = ContactsRoute()
    override val graphRoute: Any = ContactsGraph
    override val drawsGroupChrome: Boolean = true
    override val settingsRoute: Any = ContactsSettingsRoute
    override val capturePlaceholderRes: Int = R.string.contacts_capture_placeholder

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<ContactsGraph>(startDestination = ContactsRoute()) {
            composable<ContactsRoute>(
                deepLinks = listOf(
                    navDeepLink { uriPattern = ContactsLinks.CONTACTS },
                    navDeepLink { uriPattern = "${ContactsLinks.CONTACTS}/{tab}" },
                ),
            ) { entry -> StartScreen(nav, entry.arguments) }
            composable<ContactDetailRoute>(deepLinks = listOf(navDeepLink { uriPattern = ContactsLinks.CONTACT })) {
                ContactDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { id, raw -> nav.navigate(ContactEditRoute(id, raw ?: -1L)) },
                )
            }
            composable<ContactEditRoute> { ContactEditScreen(onDone = { nav.popBackStack() }) }
        }
        // Reached from Settings, so they stay outside the contacts graph (the bar keeps Settings selected).
        composable<ContactsSettingsRoute> { ContactsSettingsScreen(onBack = { nav.popBackStack() }) }
        composable<BirthdaysSettingsRoute> { BirthdaysSettingsScreen(onBack = { nav.popBackStack() }) }
    }

    @Composable
    override fun StartScreen(nav: NavController, args: android.os.Bundle?) {
        ContactsScreen(
            initialTab = args?.getString("tab"),
            onOpen = { id, raw -> nav.navigate(ContactDetailRoute(id, raw ?: -1L)) },
            onNew = { nav.navigate(ContactEditRoute()) },
            onSettings = { nav.navigate(ContactsSettingsRoute) },
        )
    }

    /** Off: the phone's copy under the Klean account goes. On: it comes back. Then the surfaces follow. */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            mirror.reschedule()
            status.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "contacts toggle failed", e)
        }
    }
}

/**
 * Birthdays as the registry sees it: its own gate (reminders and surfaces can go off alone), the Birthdays card on
 * Today (slot BIRTHDAYS), settings, but no nav item: it is a tab in Contacts (plan H5).
 */
@Singleton
class BirthdaysKleanModule @Inject constructor(
    private val reminders: BirthdayReminderSync,
    private val surfaces: BirthdaySurfaces,
) : KleanModule {
    override val id: ModuleId = ModuleId.BIRTHDAYS
    override val labelRes: Int = R.string.birthdays_label
    override val icon: ImageVector get() = Icons.Rounded.Cake
    override val navOrder: Int = 35
    override val dashboardSlot: DashboardSlot = DashboardSlot.BIRTHDAYS
    override val inNavigation: Boolean = false
    override val startRoute: Any = ContactsRoute(ContactsLinks.TAB_BIRTHDAYS)
    override val graphRoute: Any = ContactsGraph
    override val drawsGroupChrome: Boolean = true
    override val settingsRoute: Any = BirthdaysSettingsRoute

    /** Contacts registers the shared graph. */
    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = Unit

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = BirthdaysCard(onOpen, modifier)

    /** Off: every birthday reminder is removed. On: they are armed again. Then the surfaces follow. */
    override suspend fun onEnabledChanged(enabled: Boolean) {
        try {
            reminders.reschedule()
            surfaces.onContactsChanged(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Klean", "birthdays toggle failed", e)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactsBindings {
    @Binds @IntoSet abstract fun contacts(m: ContactsKleanModule): KleanModule
    @Binds @IntoSet abstract fun birthdays(m: BirthdaysKleanModule): KleanModule
    @Binds abstract fun repository(impl: DefaultContactRepository): ContactRepository
    @Binds abstract fun device(impl: AndroidDeviceContacts): DeviceContacts
    @Binds abstract fun join(impl: ContactsMirrorHook): ProviderJoin
    @Binds abstract fun birthdayText(impl: AndroidBirthdayText): BirthdayText
    @Binds abstract fun surfaceText(impl: AndroidBirthdaySurfaceText): BirthdaySurfaceText
    @Binds abstract fun vcf(impl: AndroidVcfFiles): VcfFiles
    @Binds abstract fun access(impl: app.klean.feature.contacts.ui.MirrorAccessHook): app.klean.feature.contacts.ui.ContactsAccessHook
    @Binds @javax.inject.Named(app.klean.feature.contacts.ui.BirthdaysSettingsViewModel.BIRTHDAY_RESYNC)
    abstract fun birthdayResync(impl: app.klean.feature.contacts.ui.BirthdayResync): Reschedulable

    @Binds @IntoSet abstract fun capture(h: ContactsCaptureHandler): CaptureHandler
    @Binds @IntoSet abstract fun onboarding(s: ContactsOnboardingStep): OnboardingStepProvider

    @Multibinds abstract fun contactListeners(): Set<ContactChangeListener>
    @Binds @IntoSet abstract fun mirrorListener(h: ContactsMirrorHook): ContactChangeListener
    @Binds @IntoSet abstract fun mirrorResync(h: ContactsMirrorHook): Reschedulable
    @Binds @IntoSet abstract fun reminderListener(s: BirthdayReminderSync): ContactChangeListener
    @Binds @IntoSet abstract fun reminderChanges(s: BirthdayReminderSync): ReminderChangeListener
    @Binds @IntoSet abstract fun reminderResync(s: BirthdayReminderSync): Reschedulable

    @Binds @IntoSet abstract fun surfacesListener(s: BirthdaySurfaces): ContactChangeListener
    @Binds @IntoSet abstract fun status(s: BirthdaySurfaces): StatusLineProvider
    @Binds @IntoSet abstract fun today(s: BirthdaySurfaces): TodayItemsProvider
    @Binds @IntoSet abstract fun summary(s: BirthdaySurfaces): DashboardSummaryProvider
    @Binds @IntoSet abstract fun activity(s: BirthdaySurfaces): DayActivityProvider
    @Binds @IntoSet abstract fun dayItems(s: BirthdaySurfaces): DayItemsProvider
}
