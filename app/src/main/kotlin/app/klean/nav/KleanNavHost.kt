package app.klean.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.core.ui.module.KleanModule
import app.klean.feature.onboarding.OnboardingRoute
import app.klean.feature.onboarding.OnboardingScreen
import app.klean.core.google.ui.AccountMenuScreen
import app.klean.core.capture.voice.voiceScreen
import app.klean.feature.settings.AboutSettingsRoute
import app.klean.feature.settings.AiProvidersSettingsRoute
import app.klean.feature.settings.SettingsScreen
import app.klean.feature.settings.settingsGraph
import app.klean.today.ArrangeTodayScreen
import app.klean.today.TodayScreen
import app.klean.track.TrackWeekScreen
import app.klean.widget.TodayWidgetReceiver

/**
 * Every module's routes are registered whether it is on or off, so the graph never changes at runtime.
 * Rebuilding it on a toggle reset the back stack to Today, and a route that disappeared crashed any
 * lingering link to it. Turning a module off hides its entry points (bottom bar, dashboard, settings
 * list), not its routes.
 *
 * The root is [PagesRoute], whose screen is the shell's pager ([pages]): Today, then each group's tabs. Every other
 * destination (details, Settings, the account menu, voice, Arrange Today, the wizard) opens over it. The pages' own
 * routes (Today, Track's Week, each module's start route) stay registered for links; the shell turns reaching one into
 * showing its page.
 *
 * [startWithSetup]: a first run opens on the setup wizard; it is also reachable from Settings.
 */
@Composable
fun KleanNavHost(
    nav: NavHostController,
    shell: ShellViewModel,
    enabled: List<KleanModule>,
    startWithSetup: Boolean = false,
    pages: @Composable () -> Unit = {},
) {
    val registry = shell.registry
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val motion = androidx.compose.runtime.remember(ctx, registry) {
        KleanMotion.from(ctx, instant = setOf(TodayRoute::class, TrackWeekRoute::class) + registry.all.map { it.startRoute::class })
    }
    NavHost(
        navController = nav,
        startDestination = if (startWithSetup) OnboardingRoute else PagesRoute,
        enterTransition = motion.enterTransition,
        exitTransition = motion.exitTransition,
        popEnterTransition = motion.popEnterTransition,
        popExitTransition = motion.popExitTransition,
    ) {
        composable<PagesRoute> { pages() }
        composable<OnboardingRoute> {
            OnboardingScreen(
                registry = registry,
                quietHours = shell.quietHours,
                onboarding = shell.onboarding,
                permissions = shell.permissions,
                onFinished = {
                    // Clears the wizard (and, when re-run from Settings, the stack under it): the pages are home.
                    nav.navigate(PagesRoute) {
                        popUpTo(nav.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenAiSettings = { nav.navigate(AiProvidersSettingsRoute) },
                appWidgets = listOf(TodayWidgetReceiver::class.java),
                steps = shell.onboardingSteps.toList(),
            )
        }
        composable<TodayRoute>(deepLinks = listOf(navDeepLink { uriPattern = "klean://today" })) { TodayPage(nav, shell) }
        composable<AccountRoute> {
            // Settings and Help replace the menu, so Back from them returns to the pages (as in Google's apps).
            val leaveMenu: androidx.navigation.NavOptionsBuilder.() -> Unit = {
                popUpTo<PagesRoute>()
                launchSingleTop = true
            }
            AccountMenuScreen(
                actions = shell.account,
                onClose = { nav.popBackStack() },
                onSettings = { nav.navigate(SettingsRoute, leaveMenu) },
                onHelp = { nav.navigate(AboutSettingsRoute, leaveMenu) },
            )
        }
        composable<TrackWeekRoute> { TrackWeekPage(shell) }
        composable<ArrangeTodayRoute> {
            ArrangeTodayScreen(registry = registry, layout = shell.todayLayout, onDone = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(modules = enabled, onOpen = { nav.navigate(it) }, onRunSetup = { nav.navigate(OnboardingRoute) }, onBack = { nav.popBackStack() })
        }
        settingsGraph(nav, registry, shell.permissions)
        voiceScreen(nav)
        registry.all.forEach { it.registerGraph(this, nav) }
    }
}

/** Track › Week, as a page and as its route. */
@Composable
fun TrackWeekPage(shell: ShellViewModel) {
    TrackWeekScreen(summaries = shell.weekSummaries, clock = shell.clock, zone = shell.zone, ticks = { shell.ticker.ticks() })
}

/**
 * Today, as the pager's first page and as its route. Today's one-tap grid: capture opens the sheet in place (never
 * CaptureActivity, whose Back zooms out), Speak opens the voice screen.
 */
@Composable
fun TodayPage(nav: NavController, shell: ShellViewModel) {
    val registry = shell.registry
    val launcher = app.klean.core.capture.rememberCaptureLauncher()
    fun capture(target: app.klean.shared.module.ModuleId?) =
        launcher.open(target, app.klean.shared.diet.CaptureSource.DASHBOARD, false, null)
    TodayScreen(
        registry = registry, glance = shell.glance,
        clock = shell.clock, zone = shell.zone, ticker = shell.ticker,
        onQuickAction = { a ->
            when (a) {
                is app.klean.core.ui.module.QuickAction.Capture -> capture(a.module)
                is app.klean.core.ui.module.QuickAction.Open -> nav.navigate(a.route)
                is app.klean.core.ui.module.QuickAction.Instant -> Unit // runs on Today itself, with Undo
            }
        },
        onVoice = { nav.navigate(app.klean.core.capture.voice.VoiceRoute()) { launchSingleTop = true } },
        onType = { capture(null) },
        onOpen = { link -> runCatching { nav.navigate(android.net.Uri.parse(link)) } },
        layout = shell.todayLayout,
        onArrange = { nav.navigate(ArrangeTodayRoute) },
        onSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
        account = shell.account,
        onAccount = { nav.navigate(AccountRoute) { launchSingleTop = true } },
        leftHandedFlow = shell.devicePrefs?.leftHanded ?: kotlinx.coroutines.flow.flowOf(false),
    )
    NewModulesSheet(registry, shell.newModules)
}
