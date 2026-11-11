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
import androidx.compose.runtime.rememberUpdatedState
import kotlin.reflect.KClass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.klean.core.capture.LocalCaptureLauncher
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.GroupChrome
import app.klean.core.design.component.GroupChromeResolver
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.component.LocalGroupChrome
import app.klean.core.design.component.LocalLeftHanded
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.OverflowMenu
import app.klean.core.design.component.SegmentedTabs
import app.klean.core.design.theme.Space
import app.klean.core.ui.module.KleanModule
import app.klean.feature.contacts.ContactEditRoute
import app.klean.feature.onboarding.OnboardingRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * A group on the bar with its enabled, navigable modules (Reminders and Birthdays live inside Tasks and Contacts).
 * Track with two or more trackers starts its tabs with the Week overview.
 */
private data class BarGroup(val group: NavGroup, val modules: List<KleanModule>) {
    val overview: Boolean get() = group.hasOverview(modules.size)
    private val offset: Int get() = if (overview) 1 else 0

    /** The route of tab [i]: the overview first, then the modules. */
    fun route(i: Int): Any = if (overview && i == 0) TrackWeekRoute else modules[i - offset].startRoute

    /** The tab index of module [i]. */
    fun tabOf(i: Int): Int = i + offset

    /** How many tabs the group shows (the overview, then its modules). */
    val tabCount: Int get() = modules.size + offset

    fun landing(last: Map<String, String>): Any? = when (val k = landingKey(group, modules.map { it.id.value }, last)) {
        null -> null
        WEEK_KEY -> TrackWeekRoute
        else -> modules.first { it.id.value == k }.startRoute
    }
}

/**
 * The app shell: a bottom bar of groups (Today, Plan, Track, People; UI plan decision A), the Add menu for every
 * top-level screen, and the group chrome (title and module tabs) for module screens.
 */
@Composable
fun KleanAppUi(
    vm: ShellViewModel = hiltViewModel(),
    deepLinks: Flow<Intent> = emptyFlow(),
    /** The capture sheet shown in place (tests pass a stand-in: the real one needs Hilt). */
    captureSheet: (@Composable (CaptureRequest, () -> Unit) -> Unit)? = null,
) {
    val sheets = remember { ShellSheets() }
    val registry = vm.registry
    val enabled by registry.enabled.collectAsStateWithLifecycle(initialValue = emptyList())
    val startWithSetup by vm.startWithSetup.collectAsStateWithLifecycle()
    val last by vm.groupMemory.last.collectAsStateWithLifecycle(initialValue = emptyMap())
    // Left-handed (Settings › Appearance) mirrors the Add button and the group toolbar on every module screen.
    val leftHandedFlow = remember(vm) { vm.devicePrefs?.leftHanded ?: kotlinx.coroutines.flow.flowOf(false) }
    val leftHanded by leftHandedFlow.collectAsStateWithLifecycle(initialValue = false)
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val scope = rememberCoroutineScope()
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
        if (current.isOn(TrackWeekRoute)) vm.groupMemory.remember(NavGroup.TRACK, WEEK_KEY)
    }
    val selectedGroup = groups.firstOrNull { g -> g.modules.any { current.isOn(it.graphRoute) } }?.group
        ?: NavGroup.TRACK.takeIf { current.isOn(TrackWeekRoute) }
        ?: NavGroup.TODAY

    // The pages, left to right: Today, then each group's tabs (modules that are off are already left out). A sideways
    // swipe moves along them, and the slide between two of them runs in this order.
    val pages: List<Any> = remember(groups) {
        listOf<Any>(TodayRoute) + groups.filter { it.group != NavGroup.TODAY }.flatMap { g -> List(g.tabCount) { g.route(it) } }
    }
    val pageClasses: List<KClass<*>> = remember(pages) { pages.map { it::class } }
    val pageOrder by rememberUpdatedState(pageClasses)
    val currentPage = pageClasses.firstOrNull { current?.hasRoute(it) == true }

    val settings = MenuAction(stringResource(R.string.nav_settings)) { nav.navigate(SettingsRoute) { launchSingleTop = true } }
    val titles = NavGroup.entries.associateWith { stringResource(it.labelRes) }
    val moduleLabels = enabled.associate { it.id to stringResource(it.labelRes) }
    val weekLabel = stringResource(R.string.track_week)
    fun open(route: Any) = nav.navigateTopLevel(route)

    /** To page [cls]: Today the one way to Today, any other page as its tab would. */
    fun openPage(cls: KClass<*>) {
        val route = pages[pageClasses.indexOf(cls)]
        if (route == TodayRoute) nav.goToday() else open(route)
    }

    /** The group and tab index of a group's start screen (a module's, or Track's Week), or null. */
    fun chromeFor(destination: NavDestination?): Pair<BarGroup, Int>? {
        for (g in groups) {
            if (g.overview && destination?.hasRoute(TrackWeekRoute::class) == true) return g to 0
            val i = g.modules.indexOfFirst { m -> destination?.hasRoute(m.startRoute::class) == true }
            if (i >= 0) return g to g.tabOf(i)
        }
        return null
    }
    fun chrome(g: BarGroup, i: Int) = GroupChrome(
        title = titles.getValue(g.group),
        tabs = (if (g.overview) listOf(weekLabel) else emptyList()) + g.modules.map { moduleLabels[it.id].orEmpty() },
        selected = i,
        onSelect = { j -> open(g.route(j)) },
        menu = listOf(settings),
    )
    val resolver = GroupChromeResolver { owner -> (owner as? NavBackStackEntry)?.destination?.let(::chromeFor)?.let { (g, i) -> chrome(g, i) } }

    val enabledIds = enabled.map { it.id }.toSet()
    // The module whose screens are showing; its own Add line sits nearest the button.
    val here = enabled.firstOrNull { current.isOn(it.graphRoute) }?.id
    val addSpec = AddMenuSpec(
        items = addMenuEntries(enabledIds, current = here).map { e ->
            val label = stringResource(e.label)
            AddMenuItem(e.module.value, label, e.icon) {
                if (e.opensContactEditor) {
                    nav.navigate(ContactEditRoute())
                } else {
                    sheets.capture = CaptureRequest(e.module)
                }
            }
        },
        onCapture = { sheets.capture = CaptureRequest(target = null) },
    )

    // The wizard, the account menu and the voice screen are full screen: no bar while they show, or while the start screen is
    // still being decided.
    val inSetup = startWithSetup == null || current.isOn(OnboardingRoute) || current.isOn(AccountRoute) ||
        current.isOn(app.klean.core.capture.voice.VoiceRoute())
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
                            nav.goToday()
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
        val legacy = chromeFor(current)?.takeIf { (g, i) ->
            val module = if (g.overview && i == 0) null else g.modules[i - (if (g.overview) 1 else 0)]
            module != null && !module.drawsGroupChrome && g.modules.size > 1
        }
        CompositionLocalProvider(
            LocalGroupChrome provides resolver,
            LocalAddMenu provides addSpec.takeIf { !inSetup },
            LocalCaptureLauncher provides sheets.launcher,
            LocalLeftHanded provides leftHanded,
        ) {
            Column {
                if (legacy != null) LegacyGroupHeader(chrome(legacy.first, legacy.second))
                // Full width whatever the page draws, so a swipe anywhere across it lands here.
                val host = Modifier.fillMaxWidth().weight(1f).let { if (legacy != null) it.consumeWindowInsets(WindowInsets.statusBars) else it }
                Box(host.pageSwipe(isPage = currentPage != null && !inSetup) { dx -> pageAfter(pageClasses, currentPage, dx)?.let(::openPage) }) {
                    startWithSetup?.let {
                        KleanNavHost(
                            nav = nav, shell = vm, enabled = enabled, startWithSetup = it,
                            // A Today card opens its group where the user left it, like the bar does.
                            onOpenGroup = { g -> groups.firstOrNull { b -> b.group == g }?.landing(last)?.let { open(it) } },
                            pageOrder = { pageOrder },
                        )
                    }
                }
            }
            // The sheet's mic closes it and opens the voice screen as its own page (it slides up; Back slides it down).
            ShellSheetsHost(
                sheets,
                captureSheet ?: { r, d ->
                    InAppCaptureSheet(r, d, onVoice = { t ->
                        sheets.capture = null
                        nav.navigate(app.klean.core.capture.voice.VoiceRoute(t?.value)) { launchSingleTop = true }
                    })
                },
            )
        }
    }
}

/** Title, Settings and module tabs above a module screen that doesn't draw them itself (none once Phase 3 is done). */
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

/**
 * The one way to Today (the bar, a swipe, anything else): pop back to it, since it is the root of every stack. Only when
 * it isn't on the stack does it start a new stack on Today. [navigateTopLevel] can't do it: its `popUpTo(Today)` saves
 * the page being left under Today's id, and `restoreState` then put that page straight back, so Today did nothing.
 */
fun NavController.goToday() {
    if (!popBackStack(TodayRoute, inclusive = false)) {
        navigate(TodayRoute) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
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
