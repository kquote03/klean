package app.klean.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import app.klean.core.ui.module.KleanModule
import app.klean.feature.settings.SettingsScreen
import app.klean.feature.settings.settingsGraph
import app.klean.today.TodayScreen

/**
 * Every module's routes are registered whether it is on or off, so the graph never changes at runtime.
 * Rebuilding it on a toggle reset the back stack to Today, and a route that disappeared crashed any
 * lingering link to it. Turning a module off hides its entry points (bottom bar, dashboard, settings
 * list), not its routes.
 */
@Composable
fun KleanNavHost(nav: NavHostController, shell: ShellViewModel, enabled: List<KleanModule>) {
    val registry = shell.registry
    NavHost(navController = nav, startDestination = TodayRoute) {
        composable<TodayRoute>(deepLinks = listOf(navDeepLink { uriPattern = "klean://today" })) { TodayScreen(registry = registry, capture = shell.capture, clock = shell.clock, onOpen = { nav.navigate(it) }) }
        composable<SettingsRoute> { SettingsScreen(modules = enabled, onOpen = { nav.navigate(it) }) }
        settingsGraph(nav, registry)
        registry.all.forEach { it.registerGraph(this, nav) }
    }
}
