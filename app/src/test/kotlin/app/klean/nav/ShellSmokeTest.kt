package app.klean.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isSelectable
import app.klean.shared.module.ModuleId
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.theme.KleanTheme
import app.klean.shared.capture.DefaultCaptureRouter
import kotlin.time.Clock
import app.klean.core.ui.module.KleanModule
import app.klean.feature.diet.DietKleanModule
import app.klean.feature.diet.DietEnablement
import org.junit.Assert.assertEquals
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.TodayGlanceProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.test.onAllNodesWithContentDescription
import app.klean.feature.diet.ui.MinuteTicker
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import app.klean.core.ui.module.ModuleRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import app.klean.core.ui.module.DashboardSlot
import app.klean.today.InMemoryTodayLayout
import app.klean.today.TodayLayoutStore
import androidx.compose.material3.Text
import androidx.navigation.compose.composable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Stand-in for the on-device check: the bar of groups, Settings from Today, and the settings skeleton. */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun shell(
        modules: Set<KleanModule>,
        gate: FakeModuleGate = FakeModuleGate(),
        dashboard: DashboardContributions? = null,
        layout: TodayLayoutStore = InMemoryTodayLayout(),
        newModules: NewModulesStore = InMemoryNewModules(),
        groupMemory: NavGroupMemory = InMemoryNavGroupMemory(),
        glance: TodayGlance = TodayGlance(emptySet(), gate),
    ): ShellViewModel =
        ShellViewModel(
            ModuleRegistry(modules, gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System,
            FakePermissionChecker(), FakeOnboardingState(show = false), FakeQuietHoursStore(),
            dashboard = dashboard ?: DashboardContributions(emptySet(), emptySet(), gate),
            ticker = OneTick,
            todayLayout = layout,
            newModules = newModules,
            groupMemory = groupMemory,
            glance = glance,
        )

    /** One clock read; the real ticker loops once a minute forever. */
    private object OneTick : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    // The real dashboard card needs Hilt; the shell only needs the module's metadata and routes.
    private fun diet(): KleanModule = object : KleanModule by DietKleanModule(DietEnablement { }) {
        @Composable override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
    }

    /** Today's avatar opens the account menu; Settings is its first "More from Klean" row. */
    private fun openSettings() {
        compose.onNodeWithContentDescription("Account and settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("More from Klean").assertExists()
        // Full screen, like Google's account menu: no navigation bar.
        compose.onAllNodes(hasClickAction() and hasText("Today")).assertCountEquals(0)
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
    }

    private fun openModulesScreen() {
        openSettings()
        compose.waitForIdle()
        compose.onNodeWithText("Turn modules on or off").performClick()
        compose.waitForIdle()
    }

    /** Found on device: toggling a module rebuilt the nav graph and dropped the user back on Today. */
    @Test fun turningAModuleOffKeepsYouOnTheModulesScreen() {
        val vm = shell(setOf(diet()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        openModulesScreen()
        compose.onNode(isToggleable()).performClick()
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertDoesNotExist()
    }

    /** Found on device: Settings listed a disabled module whose routes were gone, and tapping it crashed. */
    @Test fun disabledModuleDisappearsFromSettingsList() {
        val vm = shell(setOf(diet()), FakeModuleGate(setOf(ModuleId.DIET)))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        openSettings()
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onAllNodesWithText("Diet").assertCountEquals(0)
    }

    /** Found on Waydroid: History, Day and Entry screens left Diet unselected in the bottom bar. */
    @Test fun moduleStaysSelectedOnItsInnerScreens() {
        val module = NestedModule()
        val vm = shell(setOf(module))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Track")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open inner").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Inner screen").assertExists()
        compose.onNode(hasClickAction() and hasText("Track")).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("Today")).assertIsNotSelected()
    }

    @Test fun settingsPermissionsShowsLiveStatusRows() {
        val vm = shell(emptySet())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        openSettings()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Permissions"))
        compose.onNodeWithText("Permissions").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Alarms and reminders").assertExists()
        assertTrue(compose.onAllNodesWithText("Off").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun shellShowsTodayAndSettingsWithEmptyState() {
        val vm = shell(emptySet())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
        openSettings()
        assertTrue(compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onNodeWithText("Turn modules on or off").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No modules are installed yet.").assertIsDisplayed()
    }

    @Test fun dietAloneBringsTheTrackGroup() {
        val vm = shell(setOf(diet()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        assertEquals(listOf("Today", "Track"), barLabels())
    }

    // Today: one card per group, built from the modules' tiles; a card opens its group.
    @Config(qualifiers = "w411dp-h891dp")
    /** Today's whiteboard: a module's foods by name, and its one-tap tile opening the module's own screen. */
    @Test fun todayShowsTheDayAndATileOpensItsModule() {
        val gate = FakeModuleGate()
        val diet = dietGlance(QuickAction.Open(ModuleId.DIET, "Food", Icons.Rounded.Star, NestedStart))
        val vm = shell(setOf(dietWithCard()), gate, glance = TodayGlance(setOf(diet), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("shawarma").assertExists()
        // Today has no Add button: the grid replaces it.
        compose.onAllNodesWithContentDescription("Add").assertCountEquals(0)
        compose.onNodeWithText("Food").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open inner").assertExists()
        compose.onNode(hasClickAction() and hasText("Track")).assertIsSelected()
        // Today in the bar comes back from a page a tile opened.
        compose.onNode(hasClickAction() and hasText("Today")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open inner").assertDoesNotExist()
        compose.onNodeWithText("shawarma").assertExists()
    }

    private fun dietGlance(action: QuickAction? = null) = object : TodayGlanceProvider {
        override val moduleId = ModuleId.DIET
        override fun glance() = flowOf(
            GlanceContribution(done = listOf(GlanceRow("diet:1", ModuleId.DIET, "shawarma", 1L)), quickAction = action),
        )
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun moduleOffHidesItsCard() {
        val gate = FakeModuleGate(setOf(ModuleId.DIET))
        val tiles = object : DashboardSummaryProvider {
            override val moduleId = ModuleId.DIET
            override fun summaries() = flowOf(listOf(SummaryItem("diet.today", "Today", "3 entries")))
        }
        val vm = shell(setOf(NestedModule()), gate, DashboardContributions(setOf(tiles), emptySet(), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("3 entries").assertDoesNotExist()
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertIsDisplayed()
    }

    private fun barLabels(): List<String> =
        compose.onAllNodes(hasClickAction() and isSelectable())
            .fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }

    /** Navigation decision A: four groups, no More; a group's tabs switch modules and the group remembers the last one. */
    @Test fun modulesSitInFourGroupsWithTabsAndMemory() {
        val memory = InMemoryNavGroupMemory()
        val vm = shell(fiveFakeModules(), groupMemory = memory)
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        assertEquals(listOf("Today", "Plan", "Track", "People"), barLabels())
        compose.onNode(hasClickAction() and hasText("Plan")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of OK").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Copy")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Copy").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Plan")).assertIsSelected()
        assertEquals(mapOf("plan" to "calendar"), memory.last.value)
        compose.onNode(hasClickAction() and hasText("Today")).performClick()
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Plan")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Copy").assertIsDisplayed()
        // A one-module group shows no tabs.
        compose.onNode(hasClickAction() and hasText("Track")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        compose.onAllNodes(hasClickAction() and hasText("Cut")).assertCountEquals(0)
    }

    /** Track opens on its Week overview until a tracker is picked, then remembers it (and Week, once picked again). */
    @Test fun trackOpensOnWeekThenRemembersTheTracker() {
        val memory = InMemoryNavGroupMemory()
        val trackers = setOf<KleanModule>(
            FakeScreenModule(ModuleId.WORKOUT, 60, android.R.string.cut, FakeRoute5) { composable<FakeRoute5> { Text("Screen of Cut") } },
            FakeScreenModule(ModuleId.SLEEP, 70, android.R.string.copy, FakeRoute3) { composable<FakeRoute3> { Text("Screen of Copy") } },
        )
        val vm = shell(trackers, groupMemory = memory)
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Track")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Your week shows up here as you log sleep, workouts or food.").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Week")).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("Track")).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("Cut")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        assertEquals("workout", memory.last.value["track"])
        compose.onNode(hasClickAction() and hasText("Today")).performClick()
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Track")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Week")).performClick()
        compose.waitForIdle()
        assertEquals(WEEK_KEY, memory.last.value["track"])
    }

    private fun todayHeader() = app.klean.today.weekday(Clock.System.todayIn(TimeZone.currentSystemDefault()))

    /** Seen on device: Today in the bar did nothing from another page (its saved state was the page being left). */
    @Test fun todayInTheBarReturnsToToday() {
        val vm = shell(fiveFakeModules())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Plan")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of OK").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Today")).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Screen of OK").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("Today")).assertIsSelected()
        // Again, after Plan then Track (the path Back audits on device).
        compose.onNode(hasClickAction() and hasText("Plan")).performClick()
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Track")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Today")).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        compose.onNodeWithText("Screen of Cut").assertDoesNotExist()
    }

    /** The same from a module laid out in its own graph (Diet, Sleep), reached through Track's tabs. */
    @Test fun todayInTheBarReturnsFromANestedModule() {
        val vm = shell(fiveFakeModules() + NestedModule())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        repeat(2) {
            compose.onNode(hasClickAction() and hasText("Track")).performClick()
            compose.waitForIdle()
            compose.onNode(hasClickAction() and hasText("Diet")).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Open inner").assertIsDisplayed()
            compose.onNode(hasClickAction() and hasText("Today")).performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
            compose.onNodeWithText("Open inner").assertDoesNotExist()
        }
    }

    @Test fun aGroupWithEveryModuleOffLeavesTheBar() {
        val vm = shell(fiveFakeModules(), FakeModuleGate(setOf(ModuleId.WORKOUT)))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        assertEquals(listOf("Today", "Plan", "People"), barLabels())
    }

    /** Diet laid out like the real one, with a visible card. */
    private fun dietWithCard(): KleanModule = object : KleanModule by NestedModule() {
        override val dashboardSlot = DashboardSlot.DIET
        @Composable override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) { Text("Diet card") }
    }

    /** M16.5: hiding Diet on Today (Arrange Today) removes its card and keeps the Track tab where it is. */
    @Test fun hidingTheDietCardKeepsTheTrackNavItem() {
        val layout = InMemoryTodayLayout()
        val gate = FakeModuleGate()
        val vm = shell(setOf(dietWithCard()), gate, layout = layout, glance = TodayGlance(setOf(dietGlance()), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("shawarma").assertExists()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Arrange Today").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Show Diet").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(setOf("diet"), layout.hidden.value)
        compose.onNodeWithText("shawarma").assertDoesNotExist()
        compose.onNode(hasClickAction() and hasText("Track")).assertExists()
    }

    /** M16.6: an update's new modules are listed once on Today, already on; a switch turns one off. */
    @Test fun newInKleanListsNewModulesOnceAndCanTurnOneOff() {
        val gate = FakeModuleGate()
        val store = InMemoryNewModules(setOf(ModuleId.DIET))
        val vm = shell(setOf(dietWithCard()), gate, newModules = store)
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("New in Klean").assertExists()
        compose.onNodeWithContentDescription("Use Diet").assertIsOn()
        compose.onNodeWithContentDescription("Use Diet").performClick()
        compose.waitForIdle()
        assertEquals(setOf(ModuleId.DIET), gate.state.value)
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<ModuleId>(), store.pending.value)
        compose.onNodeWithText("New in Klean").assertDoesNotExist()
    }
}
