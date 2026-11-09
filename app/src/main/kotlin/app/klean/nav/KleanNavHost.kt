package app.klean.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.core.ui.module.KleanModule
import app.klean.feature.onboarding.OnboardingRoute
import app.klean.feature.onboarding.OnboardingScreen
import app.klean.feature.settings.AiProvidersSettingsRoute
import app.klean.feature.settings.SettingsScreen
import app.klean.feature.settings.settingsGraph
import app.klean.today.TodayScreen

/**
 * Every module's routes are registered whether it is on or off, so the graph never changes at runtime.
 * Rebuilding it on a toggle reset the back stack to Today, and a route that disappeared crashed any
 * lingering link to it. Turning a module off hides its entry points (bottom bar, dashboard, settings
 * list), not its routes.
 *
 * [startWithSetup]: a first run opens on the setup wizard; it is also reachable from Settings.
 */
@Composable
fun KleanNavHost(nav: NavHostController, shell: ShellViewModel, enabled: List<KleanModule>, startWithSetup: Boolean = false) {
    val registry = shell.registry
    NavHost(navController = nav, startDestination = if (startWithSetup) OnboardingRoute else TodayRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                registry = registry,
                quietHours = shell.quietHours,
                onboarding = shell.onboarding,
                permissions = shell.permissions,
                onFinished = {
                    // Clears the wizard (and, when re-run from Settings, the stack under it): Today is home.
                    nav.navigate(TodayRoute) {
                        popUpTo(nav.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenAiSettings = { nav.navigate(AiProvidersSettingsRoute) },
            )
        }
        composable<TodayRoute>(deepLinks = listOf(navDeepLink { uriPattern = "klean://today" })) {
            TodayScreen(
                registry = registry, capture = shell.capture, clock = shell.clock,
                dashboard = shell.dashboard, zone = shell.zone, ticker = shell.ticker,
                onOpen = { nav.navigate(it) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(modules = enabled, onOpen = { nav.navigate(it) }, onRunSetup = { nav.navigate(OnboardingRoute) })
        }
        settingsGraph(nav, registry, shell.permissions)
        registry.all.forEach { it.registerGraph(this, nav) }
    }
}
