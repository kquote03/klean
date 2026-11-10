package app.klean.nav

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.klean.R
import app.klean.core.capture.CaptureActivity
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.GroupChrome
import app.klean.core.design.component.GroupChromeResolver
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.component.LocalGroupChrome
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.OverflowMenu
import app.klean.core.design.component.SegmentedTabs
import app.klean.core.design.theme.Space
import app.klean.core.ui.module.KleanModule
import app.klean.feature.onboarding.OnboardingRoute
import app.klean.shared.diet.CaptureSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** A group on the bar with its enabled, navigable modules (Reminders and Birthdays live inside Tasks and Contacts). */
private data class BarGroup(val group: NavGroup, val modules: List<KleanModule>) {
    fun landing(last: Map<String, String>): KleanModule? =
        last[group.key]?.let { k -> modules.firstOrNull { it.id.value == k } } ?: modules.firstOrNull()
}

/**
 * The app shell: a bottom bar of groups (Today, Plan, Track, People; UI plan decision A), the Add menu for every
 * top-level screen, and the group chrome (title and module tabs) for module screens.
 */
@Composable
fun KleanAppUi(vm: ShellViewModel = hiltViewModel(), deepLinks: Flow<Intent> = emptyFlow()) {
    val registry = vm.registry
    val enabled by registry.enabled.collectAsStateWithLifecycle(initialValue = emptyList())
    val startWithSetup by vm.startWithSetup.collectAsStateWithLifecycle()
    val last by vm.groupMemory.last.collectAsStateWithLifecycle(initialValue = emptyMap())
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    LaunchedEffect(nav, deepLinks) { deepLinks.collect { nav.handleDeepLink(it) } }

    val groups = remember(enabled) {
        val byId = enabled.associateBy { it.id }
        navGroups(enabled.map { it.id }).mapNotNull { e ->
            val modules = e.modules.mapNotNull { byId[it] }.filter { it.inNavigation }
            if (e.group == NavGroup.TODAY || modules.isNotEmpty()) BarGroup(e.group, modules) else null
        }
    }
    // Remember the module the user is in, however they got there (tab, Today card, deep link).
    LaunchedEffect(current, groups) {
        for (g in groups) g.modules.firstOrNull { current.isOn(it.graphRoute) }?.let { vm.groupMemory.remember(g.group, it.id.value) }
    }
    val selectedGroup = groups.firstOrNull { g -> g.modules.any { current.isOn(it.graphRoute) } }?.group ?: NavGroup.TODAY

    val settings = MenuAction(stringResource(R.string.nav_settings)) { nav.navigate(SettingsRoute) { launchSingleTop = true } }
    val titles = NavGroup.entries.associateWith { stringResource(it.labelRes) }
    val moduleLabels = enabled.associate { it.id to stringResource(it.labelRes) }
    fun open(module: KleanModule) = nav.navigateTopLevel(module.startRoute)
    fun chromeFor(destination: NavDestination?): Pair<BarGroup, Int>? {
        for (g in groups) {
            val i = g.modules.indexOfFirst { m -> destination?.hasRoute(m.startRoute::class) == true }
            if (i >= 0) return g to i
        }
        return null
    }
    fun chrome(g: BarGroup, i: Int) = GroupChrome(
        title = titles.getValue(g.group),
        tabs = g.modules.map { moduleLabels[it.id].orEmpty() },
        selected = i,
        onSelect = { j -> open(g.modules[j]) },
        menu = listOf(settings),
    )
    val resolver = GroupChromeResolver { owner -> (owner as? NavBackStackEntry)?.destination?.let(::chromeFor)?.let { (g, i) -> chrome(g, i) } }

    val enabledIds = enabled.map { it.id }.toSet()
    val addSpec = AddMenuSpec(
        items = addMenuEntries(enabledIds).map { e ->
            val label = stringResource(e.label)
            AddMenuItem(e.module.value, label, e.icon) {
                ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.DASHBOARD, target = e.module))
            }
        },
        onCapture = { ctx.startActivity(CaptureActivity.intent(ctx, CaptureSource.DASHBOARD)) },
    )

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
            groups.forEach { g ->
                item(
                    selected = g.group == selectedGroup,
                    onClick = {
                        if (g.group == NavGroup.TODAY) {
                            nav.navigateTopLevel(TodayRoute)
                        } else {
                            g.landing(last)?.let { open(it) }
                        }
                    },
                    icon = { Icon(g.group.icon, contentDescription = null) },
                    label = { NavLabel(titles.getValue(g.group)) },
                )
            }
        },
    ) {
        // A module screen not yet built on KleanScreen gets its group's title and tabs from the shell.
        val legacy = chromeFor(current)?.takeIf { (g, i) -> !g.modules[i].drawsGroupChrome && g.modules.size > 1 }
        CompositionLocalProvider(LocalGroupChrome provides resolver, LocalAddMenu provides addSpec.takeIf { !inSetup }) {
            Column {
                if (legacy != null) LegacyGroupHeader(chrome(legacy.first, legacy.second))
                Box(if (legacy != null) Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars) else Modifier.weight(1f)) {
                    startWithSetup?.let {
                        KleanNavHost(
                            nav = nav, shell = vm, enabled = enabled, startWithSetup = it,
                            // A Today card opens its group where the user left it, like the bar does.
                            onOpenGroup = { g -> groups.firstOrNull { b -> b.group == g }?.landing(last)?.let(::open) },
                        )
                    }
                }
            }
        }
    }
}

/** Title, Settings and module tabs above a module screen that doesn't draw them itself (Track, People for now). */
@Composable
private fun LegacyGroupHeader(chrome: GroupChrome) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Space.screenEdge, end = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(chrome.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OverflowMenu(chrome.menu)
        }
        SegmentedTabs(chrome.tabs, chrome.selected, chrome.onSelect, Modifier.padding(horizontal = Space.screenEdge))
    }
}

private val NavGroup.labelRes: Int
    get() = when (this) {
        NavGroup.TODAY -> R.string.nav_today
        NavGroup.PLAN -> R.string.nav_plan
        NavGroup.TRACK -> R.string.nav_track
        NavGroup.PEOPLE -> R.string.nav_people
    }

private val NavGroup.icon
    get() = when (this) {
        NavGroup.TODAY -> Icons.Rounded.Today
        NavGroup.PLAN -> Icons.AutoMirrored.Rounded.EventNote
        NavGroup.TRACK -> Icons.Rounded.Insights
        NavGroup.PEOPLE -> Icons.Rounded.People
    }

private fun NavDestination?.isOn(route: Any): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun NavController.navigateTopLevel(route: Any) = navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(TodayRoute) { saveState = true }
}

/** A bar label on one line: at large font scales it shrinks to fit rather than breaking a word. */
@Composable
private fun NavLabel(text: String) {
    val max = MaterialTheme.typography.labelMedium.fontSize
    Text(
        text,
        maxLines = 1,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = max),
    )
}
