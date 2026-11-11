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

    // No modules: Welcome, Sleep schedule, Permissions, AI help.
    private fun render() = compose.shot("onboarding-welcome", screen = true) {
        OnboardingScreen(
            registry = ModuleRegistry(emptySet(), FakeModuleGate()),
            quietHours = FakeQuietHoursStore(),
            onboarding = FakeOnboardingState(),
            permissions = checker,
            onFinished = {},
            onOpenAiSettings = {},
        )
    }

    @Test fun pagesKeepOnePrimaryAction() {
        val shot = render()
        compose.onNodeWithText("Welcome to Klean").assertExists()
        compose.assertClutterBudget()
        compose.onNodeWithText("Set up").performClick()
        compose.onNodeWithText("Sleep schedule").assertExists()
        shot.capture("onboarding-sleep")
        compose.assertClutterBudget()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Permissions").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("AI help").assertExists()
        compose.onNodeWithText("Set up AI providers").assertExists()
        shot.capture("onboarding-ai")
        compose.assertClutterBudget()
    }
}
