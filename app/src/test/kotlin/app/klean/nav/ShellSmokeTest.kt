package app.klean.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.isToggleable
import app.klean.shared.module.ModuleId
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Stand-in for the on-device check: with no modules the shell shows Today + Settings,
 * and the settings skeleton navigates to the modules screen. */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun shell(
        modules: Set<KleanModule>,
        gate: FakeModuleGate = FakeModuleGate(),
        dashboard: DashboardContributions? = null,
    ): ShellViewModel =
        ShellViewModel(
            ModuleRegistry(modules, gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System,
            FakePermissionChecker(), FakeOnboardingState(show = false), FakeQuietHoursStore(),
            dashboard = dashboard ?: DashboardContributions(emptySet(), emptySet(), gate),
            ticker = OneTick,
        )

    /** One clock read; the real ticker loops once a minute forever. */
    private object OneTick : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    // The real dashboard card needs Hilt; the shell only needs the module's metadata and routes.
    private fun diet(): KleanModule = object : KleanModule by DietKleanModule(DietEnablement { }) {
        @Composable override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
    }

    private fun openModulesScreen() {
        compose.onAllNodes(hasClickAction() and hasText("Settings"))[0].performClick()
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
        compose.onAllNodes(hasClickAction() and hasText("Settings"))[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onAllNodesWithText("Diet").assertCountEquals(0)
    }

    /** Found on Waydroid: History, Day and Entry screens left Diet unselected in the bottom bar. */
    @Test fun moduleStaysSelectedOnItsInnerScreens() {
        val module = NestedModule()
        val vm = shell(setOf(module))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Diet")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open inner").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Inner screen").assertExists()
        compose.onNode(hasClickAction() and hasText("Diet")).assertIsSelected()
        compose.onNode(hasClickAction() and hasText("Today")).assertIsNotSelected()
    }

    @Test fun settingsPermissionsShowsLiveStatusRows() {
        val vm = shell(emptySet())
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction() and hasText("Settings"))[0].performClick()
        compose.waitForIdle()
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
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Settings").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Quiet hours").assertIsDisplayed()
        compose.onNodeWithText("Turn modules on or off").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No modules are installed yet.").assertIsDisplayed()
    }

    @Test fun dietModuleAddsBottomBarItemBetweenTodayAndSettings() {
        val vm = shell(setOf(diet()))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        val labels = compose.onAllNodes(hasClickAction() and (hasText("Today") or hasText("Diet") or hasText("Settings")))
            .fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }
        assertEquals(listOf("Today", "Diet", "Settings"), labels)
    }

    // A tall screen, so the week strip is composed in the lazy list.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun todayShowsGreetingTilesAndWeek() {
        val gate = FakeModuleGate()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val tiles = object : DashboardSummaryProvider {
            override val moduleId = ModuleId.DIET
            override fun summaries() = flowOf(listOf(SummaryItem("diet.today", "Today", "3 entries", route = NestedStart)))
        }
        val activity = object : DayActivityProvider {
            override val moduleId = ModuleId.DIET
            override fun activity(from: LocalDate, to: LocalDate) = flowOf(mapOf(today to 3))
            override fun dayRoute(date: LocalDate): Any = NestedInner
        }
        val vm = shell(setOf(NestedModule()), gate, DashboardContributions(setOf(tiles), setOf(activity), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodes(hasText("Good morning") or hasText("Good afternoon") or hasText("Good evening") or hasText("Hello"))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithText("3 entries").assertExists()
        compose.onNodeWithText("This week").assertExists()
        compose.onAllNodes(hasContentDescription("nothing logged", substring = true)).assertCountEquals(6)
        compose.onNode(hasContentDescription("3 things logged", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Inner screen").assertExists()
        compose.onNode(hasClickAction() and hasText("Diet")).assertIsSelected()
    }

    // A tall screen, so the week strip is composed in the lazy list.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun todayTileOpensItsRouteAndPreviousWeekCanComeBack() {
        val gate = FakeModuleGate()
        val tiles = object : DashboardSummaryProvider {
            override val moduleId = ModuleId.DIET
            override fun summaries() = flowOf(listOf(SummaryItem("diet.today", "Today", "3 entries", route = NestedStart)))
        }
        val activity = object : DayActivityProvider {
            override val moduleId = ModuleId.DIET
            override fun activity(from: LocalDate, to: LocalDate) = flowOf(emptyMap<LocalDate, Int>())
            override fun dayRoute(date: LocalDate): Any = NestedInner
        }
        val vm = shell(setOf(NestedModule()), gate, DashboardContributions(setOf(tiles), setOf(activity), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Previous week").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasText("Week of", substring = true)).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Next week").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("This week").assertExists()
        compose.onNodeWithText("3 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Open inner").assertExists()
    }

    // A tall screen, so the week strip is composed in the lazy list.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun moduleOffHidesItsTilesAndWeek() {
        val gate = FakeModuleGate(setOf(ModuleId.DIET))
        val tiles = object : DashboardSummaryProvider {
            override val moduleId = ModuleId.DIET
            override fun summaries() = flowOf(listOf(SummaryItem("diet.today", "Today", "3 entries")))
        }
        val activity = object : DayActivityProvider {
            override val moduleId = ModuleId.DIET
            override fun activity(from: LocalDate, to: LocalDate) = flowOf(emptyMap<LocalDate, Int>())
            override fun dayRoute(date: LocalDate): Any = NestedInner
        }
        val vm = shell(setOf(NestedModule()), gate, DashboardContributions(setOf(tiles), setOf(activity), gate))
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
        compose.onNodeWithText("3 entries").assertDoesNotExist()
        compose.onNodeWithText("This week").assertDoesNotExist()
        compose.onNodeWithText("Nothing here yet. Turn on a module in Settings.").assertIsDisplayed()
    }
}
