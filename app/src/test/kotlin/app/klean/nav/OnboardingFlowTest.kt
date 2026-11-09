package app.klean.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.design.theme.KleanTheme
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.DietEnablement
import app.klean.feature.diet.DietKleanModule
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.time.QuietHours
import kotlin.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The setup wizard inside the real shell: first run, skip, finish, and "Run setup again". */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()

    private val onboarding = FakeOnboardingState(show = true)
    private val quiet = FakeQuietHoursStore()

    // The real card and setup page need Hilt; the shell only needs the module's metadata and routes.
    private fun diet(): KleanModule = object : KleanModule by DietKleanModule(DietEnablement { }) {
        @Composable override fun DashboardCard(onOpen: () -> Unit, modifier: Modifier) {}
        @Composable override fun OnboardingContent(modifier: Modifier) {}
    }

    private fun start(show: Boolean = true) {
        onboarding.show = show
        val gate = FakeModuleGate()
        val vm = ShellViewModel(
            ModuleRegistry(setOf(diet()), gate), UniversalCaptureService(emptySet(), gate, DefaultCaptureRouter(null)), Clock.System,
            FakePermissionChecker(), onboarding, quiet,
        )
        compose.setContent { KleanTheme { KleanAppUi(vm) } }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNode(hasClickAction() and hasText(text)).performClick()
        compose.waitForIdle()
    }

    private fun assertOnToday() {
        compose.onNode(hasClickAction() and hasText("Today")).assertIsSelected()
        compose.onNodeWithText("Welcome to Klean").assertDoesNotExist()
    }

    @Test fun firstRunWalksEveryPageToToday() {
        start()
        compose.onNodeWithText("Welcome to Klean").assertExists()
        // Full screen: no bottom bar while setup runs.
        compose.onAllNodes(hasClickAction() and hasText("Settings")).assertCountEquals(0)
        tap("Set up")
        compose.onNodeWithText("What to use").assertExists()
        tap("Next")
        compose.onNodeWithText("When you sleep").assertExists()
        tap("Next")
        compose.onNodeWithText("Diet").assertExists()
        tap("Skip")
        compose.onNodeWithText("Home screen widget").assertExists()
        tap("Skip")
        compose.onNodeWithText("Permissions").assertExists()
        compose.onNodeWithText("Alarms and reminders").assertExists()
        // Delivery-app access is never asked for during setup.
        compose.onNodeWithText("Notification access").assertDoesNotExist()
        tap("Skip")
        compose.onNodeWithText("Set up AI providers").assertExists()
        compose.onNodeWithText("You can also do this later in Settings.").assertExists()
        tap("Done")
        assertOnToday()
        assertEquals(1, onboarding.done)
        assertEquals(listOf(QuietHours.DEFAULT), quiet.writes)
    }

    @Test fun skipSetupOnTheFirstPageLandsOnToday() {
        start()
        tap("Skip setup")
        assertOnToday()
        assertEquals(1, onboarding.done)
        assertEquals(emptyList<QuietHours>(), quiet.writes)
    }

    @Test fun setUpInstallOpensOnToday() {
        start(show = false)
        assertOnToday()
        assertEquals(0, onboarding.done)
    }

    @Test fun setupCanBeRunAgainFromSettings() {
        start(show = false)
        tap("Settings")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Run setup again"))
        tap("Run setup again")
        compose.onNodeWithText("Welcome to Klean").assertExists()
        tap("Skip setup")
        assertOnToday()
    }
}
