package app.klean.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.settings.SettingsScreen
import app.klean.feature.settings.settingsGraph
import app.klean.today.TodayScreen

@Composable
fun KleanNavHost(nav: NavHostController, registry: ModuleRegistry, enabled: List<KleanModule>) {
    NavHost(navController = nav, startDestination = TodayRoute) {
        composable<TodayRoute> { TodayScreen(registry = registry, onOpen = { nav.navigate(it) }) }
        composable<SettingsRoute> { SettingsScreen(onOpen = { nav.navigate(it) }) }
        settingsGraph(nav)
        registry.all.forEach { if (it in enabled) it.registerGraph(this, nav) }
    }
}
