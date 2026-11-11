package app.klean.feature.workout.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.core.design.theme.KleanTheme
import app.klean.core.testing.assertClutterBudget
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The start sheet behind Today's Workout tile: every way to start is one tap. */
@RunWith(AndroidJUnit4::class)
class WorkoutStartSheetTest {
    @get:Rule val compose = createComposeRule()

    private val push = TemplateRow(
        WorkoutTemplateEntity("t1", "Push day", WorkoutItem.encode(listOf(WorkoutItem("pushups", WorkoutKind.STRENGTH, sets = 3, reps = 12))), sync = SyncColumns(1, 1, "d", false, 1)),
        listOf(WorkoutItem("pushups", WorkoutKind.STRENGTH, sets = 3, reps = 12)),
    )

    @Test fun savedRunAndEmptyEachStartInOneTap() {
        val started = mutableListOf<String?>()
        var runs = 0
        compose.setContent {
            KleanTheme {
                WorkoutStartSheetContent(StartChoices(templates = listOf(push)), onStart = { started += it }, onContinue = {}, onRun = { runs++ }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Start a workout").assertExists()
        compose.onNodeWithText("Push day").performClick()
        compose.onNodeWithText("Run").performClick()
        compose.onNodeWithText("Empty workout").performClick()
        assertEquals(listOf("t1", null), started)
        assertEquals(1, runs)
        compose.assertClutterBudget()
    }

    @Test fun aRunningSessionIsOfferedInsteadOfASecond() {
        val continued = mutableListOf<String>()
        compose.setContent {
            KleanTheme {
                WorkoutStartSheetContent(StartChoices(openSessionId = "s1", templates = listOf(push)), onStart = {}, onContinue = { continued += it }, onRun = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Push day").assertDoesNotExist()
        compose.onNodeWithText("Continue your workout").performClick()
        assertEquals(listOf("s1"), continued)
    }
}
