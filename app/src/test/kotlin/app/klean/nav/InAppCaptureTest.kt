package app.klean.nav

import android.app.Activity
import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.theme.KleanTheme
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.module.ModuleId
import app.klean.today.InMemoryTodayLayout
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Adding from inside the app opens the capture sheet in place (plan 2026-11-11, D3). Starting CaptureActivity (its
 * own task) made Back a cross-task return, which predictive back drew as the whole window zooming out.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class InAppCaptureTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<CaptureRequest>()
    private lateinit var back: OnBackPressedDispatcher
    private var activity: Activity? = null

    private object OneTick : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    private val tasks = FakeScreenModule(ModuleId.TASKS, 10, android.R.string.ok, FakeRoute1) {
        composable<FakeRoute1> { KleanScreen(title = "Fake tasks") { Text("Fake tasks list") } }
    }

    private fun shell(): ShellViewModel {
        val gate = FakeModuleGate()
        return ShellViewModel(
            ModuleRegistry(setOf(tasks), gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System,
            FakePermissionChecker(), FakeOnboardingState(show = false), FakeQuietHoursStore(),
            dashboard = DashboardContributions(emptySet(), emptySet(), gate),
            ticker = OneTick,
            todayLayout = InMemoryTodayLayout(),
            newModules = InMemoryNewModules(),
            groupMemory = InMemoryNavGroupMemory(),
        )
    }

    private fun openPlanAddMenu() {
        val vm = shell()
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            activity = LocalActivity.current
            KleanTheme {
                KleanAppUi(vm, captureSheet = { req, _ ->
                    opened += req
                    Text("Capture sheet for ${req.target?.value ?: "anything"}")
                })
            }
        }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and hasText("Plan")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fake tasks list").assertExists()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitForIdle()
    }

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun anAddItemOpensTheSheetInPlaceWithoutAnActivity() {
        openPlanAddMenu()
        compose.onNodeWithText("Task").performClick()
        compose.waitForIdle()
        assertNull("no activity is started", shadowOf(app).nextStartedActivity)
        compose.onNodeWithText("Capture sheet for tasks").assertExists()
        assertEquals(ModuleId.TASKS, opened.last().target)
    }

    @Test fun typeOrSpeakOpensTheUntargetedSheet() {
        openPlanAddMenu()
        compose.onNodeWithText("Type or speak…").performClick()
        compose.waitForIdle()
        assertNull(shadowOf(app).nextStartedActivity)
        compose.onNodeWithText("Capture sheet for anything").assertExists()
    }

    @Test fun backClosesTheSheetOnly() {
        openPlanAddMenu()
        compose.onNodeWithText("Task").performClick()
        compose.waitForIdle()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Capture sheet for tasks").assertDoesNotExist()
        // Still on the screen under the sheet, and the app is still open.
        compose.onNodeWithText("Fake tasks list").assertExists()
        assertFalse(activity!!.isFinishing)
    }
}
