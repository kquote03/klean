package app.klean.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.notifications.permissions.PermissionStatus
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.core.ui.module.ModuleRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The setup wizard's pages in light and dark: each has one primary action (Next) and stays within the budget. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingShotsTest {
    @get:Rule val compose = createComposeRule()

    private val checker = object : PermissionChecker {
        override val sdkInt = 33
        override fun current() = PermissionStatus(true, true, false, false, false)
    }

    private val registry = ModuleRegistry(emptySet(), FakeModuleGate())
    private val quiet = FakeQuietHoursStore()
    private val state = FakeOnboardingState()

    // No modules: Welcome, Sleep schedule, Permissions, AI help. [steps] Next taps happen in the view model, so no
    // pressed button is left in the picture.
    private fun render(name: String, steps: Int = 0) {
        val vm = OnboardingViewModel(registry, quiet, state, emptyList(), emptyList())
        repeat(steps) { vm.next() }
        compose.shot(name, screen = true) {
            OnboardingScreen(
                registry = registry, quietHours = quiet, onboarding = state, permissions = checker,
                onFinished = {}, onOpenAiSettings = {}, vm = vm,
            )
        }
    }

    @Test fun welcome() {
        render("onboarding-welcome")
        compose.onNodeWithText("Welcome to Klean").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun sleepSchedule() {
        render("onboarding-sleep", steps = 1)
        compose.onNodeWithText("Sleep schedule").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun aiHelp() {
        render("onboarding-ai", steps = 3)
        compose.onNodeWithText("AI help").assertExists()
        compose.onNodeWithText("Set up AI providers").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun nextWalksThePages() {
        compose.setContent {
            OnboardingScreen(registry = registry, quietHours = quiet, onboarding = state, permissions = checker, onFinished = {}, onOpenAiSettings = {})
        }
        compose.onNodeWithText("Set up").performClick()
        compose.onNodeWithText("Sleep schedule").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Permissions").assertExists()
    }
}
