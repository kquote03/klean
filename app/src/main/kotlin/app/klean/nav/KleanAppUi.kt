package app.klean.nav

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import app.klean.core.design.component.FloatingChrome
import app.klean.core.design.component.FloatingChromeHost
import app.klean.core.design.component.GroupChrome
import app.klean.core.design.component.GroupChromeResolver
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.component.LocalFloatingChromeHost
import app.klean.core.design.component.LocalGroupChrome
import app.klean.core.design.component.LocalLeftHanded
import app.klean.core.design.component.MenuAction
import app.klean.core.ui.module.KleanModule
import app.klean.feature.contacts.ContactEditRoute
import app.klean.feature.onboarding.OnboardingRoute
import app.klean.shared.module.ModuleId
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

    /** The module of tab [i], null for the overview. */
    fun module(i: Int): KleanModule? = if (overview && i == 0) null else modules.getOrNull(i - offset)

    /** What the group remembers while tab [i] is open: the module's id, or [WEEK_KEY]. */
    fun memoryKey(i: Int): String? = if (overview && i == 0) WEEK_KEY else module(i)?.id?.value

    /** How many tabs the group shows (the overview, then its modules). */
    val tabCount: Int get() = modules.size + offset

    /** The tab a bar tap lands on: the one used last, else the overview or the first module. */
    fun landingTab(last: Map<String, String>): Int? = when (val k = landingKey(group, modules.map { it.id.value }, last)) {
        null -> null
        WEEK_KEY -> 0
        else -> modules.indexOfFirst { it.id.value == k }.takeIf { it >= 0 }?.let { it + offset }
    }
}

/**
 * The app shell: a bottom bar of groups (Today, Plan, Track, People; UI plan decision A) over a pager of the top-level
 * pages (Today, then each group's tabs), which follows the finger. The group's tabs and the Add button float over the
 * pager and stay put while the pages slide. Everything else opens over the pager through the root NavHost.
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
    val groupsNow by rememberUpdatedState(groups)
    fun barGroup(g: NavGroup) = groupsNow.firstOrNull { it.group == g }

    // The pages, left to right: Today, then each group's tabs (modules that are off are already left out).
    val pages: List<Page> = remember(groups) { pageList(groups.map { g -> g.group to List(g.tabCount) { g.route(it) } }) }
    val pagesNow by rememberUpdatedState(pages)
    val pager = rememberPagerState { pagesNow.size }
    // Arguments a link or a route brought for a page (Tasks' tab), by page key.
    val pageArgs = remember { mutableStateMapOf<String, Bundle?>() }
    // The page the chrome follows: where a drag or a fling is heading, then where it settled. Changes once per page,
    // never per frame.
    val shownIndex by remember { derivedStateOf { if (pager.isScrollInProgress) pager.targetPage else pager.settledPage } }
    val shownPage = pages.getOrNull(shownIndex)
    val onPages = current?.hasRoute(PagesRoute::class) == true
    // Over Today the chrome belongs to the page beside it (Plan's first tab), fading with Today's distance.
    val chromePage = pages.getOrNull(if (shownIndex == 0) 1 else shownIndex)
    val notOnToday by remember { derivedStateOf { pager.settledPage != 0 || pager.targetPage != 0 } }
    // A jump of more than one page cross-fades instead of flying past the pages between.
    val jumpFade = remember { Animatable(1f) }
    val ctx = LocalContext.current
    val reduced = remember(ctx) { KleanMotion.reducedOn(ctx) }

    /** Shows page [index]: over a detail, back to the pages first; a neighbour slides, a farther page jumps. */
    fun showPage(index: Int) {
        if (index !in pagesNow.indices) return
        val wasOnPages = nav.currentDestination?.hasRoute(PagesRoute::class) == true
        if (!wasOnPages) nav.toPages()
        scope.launch { pager.goTo(index, animate = wasOnPages && !reduced, jumpFade) }
    }
    val showPageNow by rememberUpdatedState(::showPage)

    // A page reached as a destination (a link, Today's rows and tiles, a module's own navigate) shows in the pager
    // instead: back to the pages, scrolled to it. The pop runs as the very next message, ahead of the next frame, so
    // the route never draws. (Popping inside the listener itself left the back-stack flow on the popped route: the
    // controller emits each entry after its listeners run.)
    DisposableEffect(nav) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val listener = NavController.OnDestinationChangedListener { controller, destination, args ->
            if (destination.hasRoute(PagesRoute::class)) return@OnDestinationChangedListener
            val list = pagesNow
            val i = list.indexOfFirst { destination.hasRoute(it.route::class) }
            if (i < 0) return@OnDestinationChangedListener
            pageArgs[list[i].key] = args?.let(::Bundle)
            val wasOnPages = controller.previousBackStackEntry?.destination?.hasRoute(PagesRoute::class) == true
            val redirect = android.os.Message.obtain(main) {
                // Only if nothing moved on in between.
                if (controller.currentDestination?.id == destination.id) {
                    controller.toPages()
                    scope.launch { pager.goTo(i, animate = wasOnPages && !reduced, jumpFade) }
                }
            }
            redirect.isAsynchronous = true
            main.sendMessageAtFrontOfQueue(redirect)
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    // The same once the pages are known: a cold-start link lands before the enabled modules have loaded.
    LaunchedEffect(backStack, pages) {
        val entry = backStack ?: return@LaunchedEffect
        if (entry.destination.hasRoute(PagesRoute::class)) return@LaunchedEffect
        val i = pages.indexOfFirst { entry.destination.hasRoute(it.route::class) }
        if (i < 0) return@LaunchedEffect
        pageArgs[pages[i].key] = entry.arguments?.let(::Bundle)
        nav.toPages()
        scope.launch { pager.goTo(i, animate = false, jumpFade) }
    }

    // The group remembers the tab the pager settles on, and the module of a detail however it was reached.
    LaunchedEffect(pager, vm) {
        snapshotFlow { pagesNow.getOrNull(pager.settledPage) }.collect { p ->
            if (p != null && p.group != NavGroup.TODAY) barGroup(p.group)?.memoryKey(p.tab)?.let { vm.groupMemory.remember(p.group, it) }
        }
    }
    LaunchedEffect(current, groups) {
        for (g in groups) g.modules.firstOrNull { current.isOn(it.graphRoute) }?.let { vm.groupMemory.remember(g.group, it.id.value) }
    }

    // Back on a page other than Today goes to Today; on Today the app leaves (no handler).
    BackHandler(enabled = onPages && notOnToday) { scope.launch { pager.goTo(0, animate = !reduced, jumpFade, alwaysSlide = true) } }

    val graphGroup = groups.firstOrNull { g -> g.modules.any { current.isOn(it.graphRoute) } }?.group
    val selectedGroup = (if (onPages) shownPage?.group else graphGroup ?: pages.getOrNull(pager.settledPage)?.group) ?: NavGroup.TODAY

    val settingsLabel = stringResource(R.string.nav_settings)
    val titles = NavGroup.entries.associateWith { stringResource(it.labelRes) }
    val moduleLabels = enabled.associate { it.id to stringResource(it.labelRes) }
    val weekLabel = stringResource(R.string.track_week)
    // Each page's chrome (title, tabs, its own tab selected); remembered so a swipe never hands pages new values.
    val chromes: Map<String, GroupChrome> = remember(pages, groups, titles, moduleLabels, weekLabel, settingsLabel) {
        val settings = MenuAction(settingsLabel) { nav.navigate(SettingsRoute) { launchSingleTop = true } }
        pages.filter { it.group != NavGroup.TODAY }.associate { p ->
            val g = groups.first { it.group == p.group }
            p.key to GroupChrome(
                title = titles.getValue(g.group),
                tabs = (if (g.overview) listOf(weekLabel) else emptyList()) + g.modules.map { moduleLabels[it.id].orEmpty() },
                selected = p.tab,
                onSelect = { j -> showPageNow(pagesNow.indexOfTab(g.group, j)) },
                menu = listOf(settings),
            )
        }
    }

    val enabledIds = enabled.map { it.id }.toSet()
    // The module whose screens are showing; its own Add line sits nearest the button. Over the pages it's the page in
    // view (the shell's floating Add button); elsewhere the module of the screen (an Add button a screen draws itself).
    val pageModule: ModuleId? = chromePage?.let { p -> barGroup(p.group)?.module(p.tab)?.id }
    val screenModule: ModuleId? = enabled.firstOrNull { current.isOn(it.graphRoute) }?.id
    val pageAdd = addSpec(enabledIds, pageModule, nav, sheets)
    val screenAdd = addSpec(enabledIds, screenModule, nav, sheets)

    // The wizard, the account menu and the voice screen are full screen: no bar while they show, or while the start screen is
    // still being decided.
    val inSetup = startWithSetup == null || current.isOn(OnboardingRoute) || current.isOn(AccountRoute) ||
        current.isOn(app.klean.core.capture.voice.VoiceRoute())
    val layoutType = if (inSetup) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    }

    val stores: PageStores = viewModel(factory = remember { viewModelFactory { initializer { PageStores() } } })
    val activityOwner = LocalViewModelStoreOwner.current
    SideEffect { stores.parent = activityOwner }
    LaunchedEffect(pages) { stores.retain(pages.map { it.key }.toSet()) }
    val floating = remember { FloatingChromeHost() }

    // One page's screen; a module's start screen, Track's Week, or Today.
    val pageContent: @Composable (Page) -> Unit = remember(nav, vm) {
        { page ->
            when (val r = page.route) {
                TodayRoute -> TodayPage(nav, vm)
                TrackWeekRoute -> TrackWeekPage(vm)
                else -> barGroup(page.group)?.module(page.tab)?.takeIf { it.startRoute::class == r::class }?.StartScreen(nav, pageArgs[page.key])
            }
        }
    }
    val shownChrome by rememberUpdatedState(chromePage?.let { chromes[it.key] })
    val showChrome by remember { derivedStateOf { pagesNow.size > 1 && (pager.isScrollInProgress || pager.settledPage != 0) } }
    val pageAddNow by rememberUpdatedState(pageAdd)
    val leftHandedNow by rememberUpdatedState(leftHanded)
    val chromesNow by rememberUpdatedState(chromes)
    // Stable, so the NavHost's graph never sees a new builder; everything that changes is read from state inside.
    val pagesScreen: @Composable () -> Unit = remember(pager, stores, floating, jumpFade, pageContent) {
        @Composable {
            Box(Modifier.fillMaxSize()) {
                PagesPager(pager, pagesNow, stores, chromesNow, floating, jumpFade, pageContent)
                // The group's tabs and the Add button: drawn once over every page, so they never slide with one.
                AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(tween(CHROME_FADE_MS)),
                    exit = fadeOut(tween(CHROME_FADE_MS)),
                    modifier = Modifier.matchParentSize(),
                ) {
                    // Fades with Today's distance from the middle, read while drawing: a drag recomposes nothing.
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 1f) }) {
                        FloatingChrome(shownChrome, pageAddNow, leftHandedNow, onClearance = { c -> if (c != null) floating.clearance = c })
                    }
                }
            }
        }
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            groups.forEach { g ->
                item(
                    selected = g.group == selectedGroup,
                    onClick = {
                        // Today always goes to Today; another group lands on the tab it was left on.
                        val i = if (g.group == NavGroup.TODAY) 0 else g.landingTab(last)?.let { pages.indexOfTab(g.group, it) } ?: -1
                        showPage(i)
                    },
                    icon = { Icon(g.group.icon, contentDescription = null) },
                    label = { NavLabel(titles.getValue(g.group)) },
                )
            }
        },
    ) {
        CompositionLocalProvider(
            LocalAddMenu provides screenAdd.takeIf { !inSetup },
            LocalCaptureLauncher provides sheets.launcher,
            LocalLeftHanded provides leftHanded,
        ) {
            startWithSetup?.let {
                KleanNavHost(nav = nav, shell = vm, enabled = enabled, startWithSetup = it, pages = pagesScreen)
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

/**
 * The pager itself. Each page gets its own view-model store, its group's chrome and the floating-chrome host (so its
 * last row clears the toolbar); the neighbours are composed before a drag starts. The scroll offset is never read
 * here: the pager places the pages itself, so a drag recomposes nothing.
 */
@Composable
private fun PagesPager(
    pager: PagerState,
    pages: List<Page>,
    stores: PageStores,
    chromes: Map<String, GroupChrome>,
    floating: FloatingChromeHost,
    jumpFade: Animatable<Float, AnimationVector1D>,
    content: @Composable (Page) -> Unit,
) {
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = jumpFade.value },
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(pager),
        key = { i -> pages.getOrNull(i)?.key ?: i },
    ) { i ->
        val page = pages.getOrNull(i) ?: return@HorizontalPager
        val chrome = chromes[page.key]
        val resolver = remember(chrome) { GroupChromeResolver { chrome } }
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides stores.owner(page.key),
            LocalGroupChrome provides resolver,
            LocalFloatingChromeHost provides floating.takeIf { page.group != NavGroup.TODAY },
        ) {
            content(page)
        }
    }
}

/**
 * Scrolls to [index]: a neighbour (or [alwaysSlide]) slides when [animate]; a farther page jumps under a short
 * cross-fade of [fade]; without [animate] it simply jumps.
 */
private suspend fun PagerState.goTo(index: Int, animate: Boolean, fade: Animatable<Float, AnimationVector1D>, alwaysSlide: Boolean = false) {
    when {
        index == currentPage && currentPageOffsetFraction == 0f -> Unit
        !animate -> scrollToPage(index)
        alwaysSlide || slidesTo(currentPage, index) -> animateScrollToPage(index)
        else -> {
            fade.animateTo(0f, tween(JUMP_FADE_MS / 2))
            scrollToPage(index)
            fade.animateTo(1f, tween(JUMP_FADE_MS / 2))
        }
    }
}

/** The Add menu, with [here]'s own line last (nearest the button); remembered while its lines stay the same. */
@Composable
private fun addSpec(enabled: Set<ModuleId>, here: ModuleId?, nav: NavController, sheets: ShellSheets): AddMenuSpec {
    val entries = addMenuEntries(enabled, current = here).map { it to stringResource(it.label) }
    return remember(entries, nav, sheets) {
        AddMenuSpec(
            items = entries.map { (e, label) ->
                AddMenuItem(e.module.value, label, e.icon) {
                    if (e.opensContactEditor) nav.navigate(ContactEditRoute()) else sheets.capture = CaptureRequest(e.module)
                }
            },
            onCapture = { sheets.capture = CaptureRequest(target = null) },
        )
    }
}

/** Back to the pages, the root of every stack; when they aren't on it (the wizard ran first), they start a new one. */
private fun NavController.toPages() {
    if (!popBackStack(PagesRoute, inclusive = false)) {
        navigate(PagesRoute) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private const val JUMP_FADE_MS = 100
private const val CHROME_FADE_MS = 150

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
