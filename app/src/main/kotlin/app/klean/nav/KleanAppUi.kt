package app.klean.nav

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.material.icons.rounded.MoreHoriz
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
import app.klean.feature.onboarding.OnboardingRoute
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType

@Composable
fun KleanAppUi(vm: ShellViewModel = hiltViewModel(), deepLinks: Flow<Intent> = emptyFlow()) {
    val registry = vm.registry
    val enabled by registry.enabled.collectAsStateWithLifecycle(initialValue = emptyList())
    val startWithSetup by vm.startWithSetup.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    LaunchedEffect(nav, deepLinks) { deepLinks.collect { nav.handleDeepLink(it) } }
    // The wizard is full screen: no bar while it runs, or while the start screen is still being decided.
    val inSetup = startWithSetup == null || current.isOn(OnboardingRoute)
    val layoutType = if (inSetup) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            item(
                selected = current.isOn(TodayRoute),
                onClick = { nav.navigateTopLevel(TodayRoute) },
                icon = { Icon(Icons.Rounded.Today, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_today)) },
            )
            val layout = navLayout(enabled)
            layout.primary.forEach { module ->
                item(
                    selected = current.isOn(module.graphRoute),
                    onClick = { nav.navigateTopLevel(module.startRoute) },
                    icon = { Icon(module.icon, contentDescription = null) },
                    label = { Text(stringResource(module.labelRes)) },
                )
            }
            if (layout.overflow.isEmpty()) {
                item(
                    selected = current.isOn(SettingsRoute),
                    onClick = { nav.navigateTopLevel(SettingsRoute) },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            } else {
                // More owns everything the other items don't: its list, the overflow modules, Settings and its pages.
                val elsewhere = current.isOn(TodayRoute) || layout.primary.any { current.isOn(it.graphRoute) }
                item(
                    selected = current != null && !elsewhere,
                    // Always lands on the list itself, not on whichever overflow page was open last.
                    onClick = { nav.navigate(MoreRoute) { launchSingleTop = true; popUpTo(TodayRoute) } },
                    icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_more)) },
                )
            }
        },
    ) {
        startWithSetup?.let { KleanNavHost(nav = nav, shell = vm, enabled = enabled, startWithSetup = it) }
    }
}

private fun NavDestination?.isOn(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun NavController.navigateTopLevel(route: Any) = navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(TodayRoute) { saveState = true }
}
