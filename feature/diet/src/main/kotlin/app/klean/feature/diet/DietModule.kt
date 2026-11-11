package app.klean.feature.diet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.klean.feature.diet.geofence.PlaceEditScreen
import app.klean.feature.diet.geofence.PlacesScreen
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import app.klean.feature.diet.notification.DietStatusActions
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.KleanModule
import app.klean.feature.diet.ui.dashboard.DietDashboardCard
import app.klean.feature.diet.ui.today.DietRow
import app.klean.core.capture.CaptureLauncher
import app.klean.core.capture.rememberCaptureLauncher
import app.klean.feature.diet.ui.entry.FoodEntryScreen
import app.klean.feature.diet.ui.history.DietDayScreen
import app.klean.feature.diet.ui.history.DietHistoryScreen
import app.klean.shared.diet.CaptureSource
import app.klean.feature.diet.ui.popAfterRemove
import app.klean.feature.diet.ui.settings.DietOnboardingContent
import app.klean.feature.diet.ui.settings.DietSettingsScreen
import app.klean.feature.diet.delivery.DeliveryAppsScreen
import app.klean.feature.diet.widget.DietWidgetReceiver
import app.klean.feature.diet.ui.today.DietScreen
import app.klean.shared.module.ModuleId
import app.klean.core.common.ZoneProvider
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.dashboard.AndroidDietSummaryText
import app.klean.feature.diet.ui.dashboard.DietDashboardProviders
import app.klean.feature.diet.ui.dashboard.DietSummaryText
import dagger.Binds
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton

/** The diet module as seen by the registry: nav item, routes, dashboard card, settings entry. */
@Singleton
class DietKleanModule @Inject constructor(private val enablement: DietEnablement) : KleanModule {
    override val id: ModuleId = ModuleId.DIET
    override val labelRes: Int = R.string.diet_label
    override val icon: ImageVector get() = Icons.Rounded.Restaurant
    override val navOrder: Int = 60
    override val dashboardSlot: DashboardSlot = DashboardSlot.DIET
    override val startRoute: Any = DietRoute
    override val graphRoute: Any = DietGraph
    override val settingsRoute: Any = DietSettingsRoute
    override val drawsGroupChrome: Boolean = true

    override fun registerGraph(builder: NavGraphBuilder, nav: NavController) = with(builder) {
        navigation<DietGraph>(startDestination = DietRoute) {
            composable<DietRoute>(deepLinks = listOf(navDeepLink { uriPattern = DietStatusActions.DEEP_LINK_DIET })) { entry ->
                StartScreen(nav, entry.arguments)
            }
            composable<DietHistoryRoute> {
                DietHistoryScreen(onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate(DietDayRoute(it.toString())) })
            }
            composable<DietDayRoute> { entry ->
                val capture = rememberCaptureLauncher()
                DietDayScreen(
                    onBack = { nav.popBackStack() },
                    onOpenEntry = { nav.navigate(FoodEntryRoute(it)) },
                    onAddForGap = { gap -> capture.openForGap(gap) },
                    backStackEntry = entry,
                )
            }
            composable<FoodEntryRoute> {
                FoodEntryScreen(onBack = { nav.popBackStack() }, onRemoved = { nav.popAfterRemove(it) })
            }
        }
        // Reached from Settings, so it stays outside the diet graph (the bar keeps Settings selected).
        composable<DietSettingsRoute> {
            DietSettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenDeliveryApps = { nav.navigate(DeliveryAppsRoute) },
                onOpenPlaces = { nav.navigate(PlacesRoute) },
            )
        }
        composable<DeliveryAppsRoute> { DeliveryAppsScreen(onBack = { nav.popBackStack() }) }
        composable<PlacesRoute> { PlacesScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(PlaceEditRoute(it)) }) }
        composable<PlaceEditRoute> { PlaceEditScreen(onBack = { nav.popBackStack() }) }
    }

    /**
     * The "entry removed · Undo" result lands on the entry under the entry screen: this page's own back-stack entry
     * in the graph, or the shell's pages entry when Diet shows as a page (both are the lifecycle owner here).
     */
    @Composable
    override fun StartScreen(nav: NavController, args: android.os.Bundle?) {
        val capture = rememberCaptureLauncher()
        DietScreen(
            onOpenEntry = { nav.navigate(FoodEntryRoute(it)) },
            onOpenHistory = { nav.navigate(DietHistoryRoute) },
            onAddForGap = { gap -> capture.openForGap(gap) },
            backStackEntry = androidx.lifecycle.compose.LocalLifecycleOwner.current as? androidx.navigation.NavBackStackEntry,
        )
    }

    @Composable
    override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) = DietDashboardCard(onOpen, modifier)

    override val onboardingTitleRes: Int = R.string.diet_onboarding_title
    override val widgetReceiver: Class<*> = DietWidgetReceiver::class.java
    override val capturePlaceholderRes: Int = R.string.diet_capture_placeholder

    @Composable
    override fun OnboardingContent(modifier: Modifier) = DietOnboardingContent(modifier)

    override suspend fun onEnabledChanged(enabled: Boolean) = enablement.onEnabledChanged(enabled)
}

/**
 * "Add something" on a gap: the capture sheet, preset to the middle of the gap ("For around 14:00 · Change"), shown
 * in place so Back stays in the app.
 */
private fun CaptureLauncher.openForGap(gap: DietRow.GapRow) =
    open(ModuleId.DIET, CaptureSource.MODULE_SCREEN, voice = false, eatenAt = gap.midpointMs)

@Module
@InstallIn(SingletonComponent::class)
abstract class DietModuleBindings {
    @Binds @IntoSet abstract fun module(m: DietKleanModule): KleanModule
    @Binds @IntoSet abstract fun week(s: app.klean.feature.diet.ui.dashboard.DietWeekSummary): app.klean.core.ui.module.WeekSummaryProvider
}

/** Diet's tiles and week activity on Today. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DietDashboardBindings {
    @Binds abstract fun summaryText(impl: AndroidDietSummaryText): DietSummaryText

    companion object {
        @Provides @Singleton @JvmStatic
        fun dashboardProviders(
            repo: DietRepository,
            fmt: DietFormatter,
            text: DietSummaryText,
            ticker: MinuteTicker,
            zone: ZoneProvider,
            prefs: DevicePrefs,
            settings: SettingsRepository,
        ): DietDashboardProviders = DietDashboardProviders(
            repo, fmt, text, ticker, zone,
            nextCheckIn = { prefs.nextPromptAt },
            promptsOn = { settings.observe(SyncedKeys.DietPromptsEnabled) },
        )

        @Provides @IntoSet @JvmStatic
        fun dietSummaries(p: DietDashboardProviders): DashboardSummaryProvider = p

        @Provides @IntoSet @JvmStatic
        fun dietActivity(p: DietDashboardProviders): DayActivityProvider = p
    }
}
