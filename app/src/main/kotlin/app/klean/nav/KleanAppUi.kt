package app.klean.nav

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.klean.R

@Composable
fun KleanAppUi(vm: ShellViewModel = hiltViewModel(), deepLinks: Flow<Intent> = emptyFlow()) {
    val registry = vm.registry
    val enabled by registry.enabled.collectAsStateWithLifecycle(initialValue = emptyList())
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    LaunchedEffect(nav, deepLinks) { deepLinks.collect { nav.handleDeepLink(it) } }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = current.isOn(TodayRoute),
                onClick = { nav.navigateTopLevel(TodayRoute) },
                icon = { Icon(Icons.Rounded.Today, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_today)) },
            )
            enabled.forEach { module ->
                item(
                    selected = current.isOn(module.startRoute),
                    onClick = { nav.navigateTopLevel(module.startRoute) },
                    icon = { Icon(module.icon, contentDescription = null) },
                    label = { Text(stringResource(module.labelRes)) },
                )
            }
            item(
                selected = current.isOn(SettingsRoute),
                onClick = { nav.navigateTopLevel(SettingsRoute) },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
            )
        },
    ) {
        KleanNavHost(nav = nav, shell = vm, enabled = enabled)
    }
}

private fun NavDestination?.isOn(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun NavController.navigateTopLevel(route: Any) = navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(TodayRoute) { saveState = true }
}
