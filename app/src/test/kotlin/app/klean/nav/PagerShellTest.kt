package app.klean.nav

import android.app.Activity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.component.GROUP_TOOLBAR_TAG
import app.klean.core.design.theme.KleanTheme
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.module.ModuleId
import app.klean.today.InMemoryTodayLayout
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Serializable object FakeDetail

/**
 * The top-level pages in one pager (lane PAGER): a drag moves the pages under the finger, the bar and the floating
 * toolbar follow the page, the toolbar stays put, and Back walks back to Today.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class PagerShellTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var back: OnBackPressedDispatcher
    private var activity: Activity? = null

    private object OneTick : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    private fun shell(modules: Set<KleanModule>, glance: Set<TodayGlanceProvider> = emptySet()): ShellViewModel {
        val gate = FakeModuleGate()
        return ShellViewModel(
            ModuleRegistry(modules, gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System,
            FakePermissionChecker(), FakeOnboardingState(show = false), FakeQuietHoursStore(),
            dashboard = DashboardContributions(emptySet(), emptySet(), gate),
            ticker = OneTick,
            todayLayout = InMemoryTodayLayout(),
            newModules = InMemoryNewModules(),
            groupMemory = InMemoryNavGroupMemory(),
            glance = TodayGlance(glance, gate),
        )
    }

    private fun start(vm: ShellViewModel) {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            activity = LocalActivity.current
            KleanTheme { KleanAppUi(vm) }
        }
        compose.waitForIdle()
    }

    private fun todayHeader() = app.klean.today.weekday(Clock.System.todayIn(TimeZone.currentSystemDefault()))

    private fun bar(label: String) = compose.onNode(hasClickAction() and hasText(label))

    private fun shown(text: String): Boolean {
        val nodes = compose.onAllNodesWithText(text)
        return (0 until nodes.fetchSemanticsNodes().size).any { nodes[it].isDisplayed() }
    }

    /** Three trackers: Track's tabs are Week, Cut, Copy, Paste. */
    private fun trackers(): Set<KleanModule> = setOf(
        fakeModule(ModuleId.WORKOUT, 60, android.R.string.cut, FakeRoute5) { Text("Screen of Cut") },
        fakeModule(ModuleId.SLEEP, 70, android.R.string.copy, FakeRoute3) { Text("Screen of Copy") },
        fakeModule(ModuleId.DIET, 80, android.R.string.paste, FakeRoute4) { Text("Screen of Paste") },
    )

    @Test fun swipingLeftOnTodayShowsPlansFirstTabAndTheBarFollows() {
        start(shell(fiveFakeModules()))
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Screen of OK").assertIsDisplayed()
        bar("Plan").assertIsSelected()
        assertFalse(shown(todayHeader()))
    }

    /** Mid-drag the page being left and the next one are both on screen, side by side. */
    @Test fun duringADragBothPagesShow() {
        start(shell(fiveFakeModules()))
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-width / 3f, 0f))
            moveBy(Offset(-width / 6f, 0f))
        }
        compose.waitForIdle()
        assertTrue("Today is still partly on screen", shown(todayHeader()))
        assertTrue("Plan's first page is coming in", shown("Screen of OK"))
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test fun trackThenTodayInTheBarLandsOnToday() {
        start(shell(fiveFakeModules()))
        bar("Track").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        bar("Today").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        bar("Today").assertIsSelected()
        assertFalse(shown("Screen of Cut"))
    }

    @Test fun backOnTrackGoesToTodayThenLeaves() {
        start(shell(fiveFakeModules()))
        bar("Track").performClick()
        compose.waitForIdle()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        bar("Today").assertIsSelected()
        assertFalse(activity!!.isFinishing)
        // On Today, Back is the system's: the shell no longer takes it.
        assertFalse(back.hasEnabledCallbacks())
    }

    /** A Today quick action that opens a detail (like New contact); Back returns to Today, the pager unchanged. */
    @Test fun aDetailFromTodayBacksOntoToday() {
        val detail = FakeScreenModule(ModuleId.CONTACTS, 50, android.R.string.paste, FakeRoute4, { Text("Screen of Paste") }) {
            composable<FakeRoute4> { Text("Screen of Paste") }
            composable<FakeDetail> { Text("A new contact") }
        }
        val action = object : TodayGlanceProvider {
            override val moduleId = ModuleId.CONTACTS
            override fun glance() = flowOf(GlanceContribution(quickAction = QuickAction.Open(ModuleId.CONTACTS, "New person", Icons.Rounded.Star, FakeDetail)))
        }
        start(shell(setOf(detail), setOf(action)))
        compose.onNodeWithText("New person").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("A new contact").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("A new contact").assertDoesNotExist()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        bar("Today").assertIsSelected()
    }

    /** A link to a page's own route shows that page in the pager: no second copy over it, Back goes to Today. */
    @Test fun aRouteToAPageScrollsThePager() {
        val action = object : TodayGlanceProvider {
            override val moduleId = ModuleId.DIET
            override fun glance() = flowOf(GlanceContribution(quickAction = QuickAction.Open(ModuleId.DIET, "Food", Icons.Rounded.Star, FakeRoute4)))
        }
        start(shell(trackers(), setOf(action)))
        compose.onNodeWithText("Food").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Paste").assertIsDisplayed()
        bar("Track").assertIsSelected()
        compose.onNode(hasClickAction() and hasText("Paste")).assertIsSelected()
        compose.runOnIdle { back.onBackPressed() }

        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
        assertFalse(back.hasEnabledCallbacks())
    }

    /** A link (here on the first frame, before the modules have loaded) opens its page in the pager. */
    @Test fun aLinkToAPageOpensItInThePager() {
        val linked = FakeScreenModule(ModuleId.CONTACTS, 50, android.R.string.paste, FakeRoute4, { Text("Screen of Paste") }) {
            composable<FakeRoute4>(deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "klean://fake-people" })) { Text("Screen of Paste") }
        }
        val vm = shell(fiveFakeModules().filter { it.id != ModuleId.CONTACTS }.toSet() + linked)
        val link = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("klean://fake-people"))
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            KleanTheme { KleanAppUi(vm, deepLinks = flowOf(link)) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Paste").assertIsDisplayed()
        bar("People").assertIsSelected()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onAllNodesWithText(todayHeader()).onFirst().assertIsDisplayed()
    }

    /** The user's ask: the tabs toolbar never moves while the pages slide under it. */
    @Test fun theToolbarStaysPutWhileSwipingWithinTrack() {
        start(shell(trackers()))
        bar("Track").performClick()
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Cut")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Screen of Cut").assertIsDisplayed()
        val toolbar = compose.onNodeWithTag(GROUP_TOOLBAR_TAG, useUnmergedTree = true)
        val before = toolbar.getUnclippedBoundsInRoot()
        val pageBefore = compose.onNodeWithText("Screen of Cut").getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput {
            down(Offset(centerX, height / 3f))
            moveBy(Offset(-width / 4f, 0f))
            moveBy(Offset(-width / 8f, 0f))
        }
        compose.waitForIdle()
        assertTrue("the page moved with the finger", compose.onNodeWithText("Screen of Cut").getUnclippedBoundsInRoot().left < pageBefore.left)
        assertEquals("mid-drag", before, toolbar.getUnclippedBoundsInRoot())
        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("after the swipe", before, compose.onNodeWithTag(GROUP_TOOLBAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot())
        compose.onNodeWithText("Screen of Copy").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Copy")).assertIsSelected()
    }

    @Test fun todayHasNoToolbarOrAddButton() {
        start(shell(trackers()))
        compose.onNodeWithTag(GROUP_TOOLBAR_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Add")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }
}
