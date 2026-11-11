package app.klean.feature.diet.ui.today

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Track › Diet in light and dark: typical, empty, long, and the overflow with History. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DietShotsTest {
    @get:Rule val compose = createComposeRule()

    private fun entry(id: String, time: String, text: String, items: List<String> = emptyList()) = DietRow.Entry(id, time, text, items, null, null)

    private val earlier = listOf(
        EarlierEntry("e1", "Lentil soup, bread", "Monday", "19:40"),
        EarlierEntry("e2", "Chicken, rice, salad", "Monday", "13:15"),
        EarlierEntry("e3", "Oatmeal, banana", "Monday", "8:05"),
        EarlierEntry("e4", "Grilled fish, potatoes", "Sunday", "20:10"),
    )

    private fun render(name: String, s: DietUiState, onOpen: (String) -> Unit = {}) = compose.shot(name, screen = true) {
        DietScreenContent(state = s, onOpenEntry = onOpen, onOpenHistory = {}, onAddForGap = {})
    }

    @Test fun typical() {
        val opened = mutableListOf<String>()
        render(
            "diet-typical",
            DietUiState(rows = listOf(entry("t1", "8:10", "Oatmeal, banana", listOf("oatmeal · 1 bowl", "banana · 1"))), earlier = earlier, loaded = true),
            onOpen = { opened += it },
        )
        compose.onNodeWithText("Earlier this week").assertExists()
        compose.onNodeWithText("Lentil soup, bread").performClick()
        assertEquals(listOf("e1"), opened)
        compose.assertClutterBudget()
    }

    @Test fun empty() {
        render("diet-empty", DietUiState(loaded = true))
        compose.onNodeWithText("Nothing yet today. What did you eat?").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun earlierOnly() {
        render("diet-earlier-only", DietUiState(earlier = earlier, loaded = true))
        compose.onNodeWithText("No entries yet").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun longContent() {
        val rows = (1..8).flatMap { i ->
            listOfNotNull(
                entry("l$i", "${7 + i}:00", "A long entry with several things in it, number $i, and a note"),
                if (i == 3) DietRow.GapRow("5 hours without an entry", 1L, 2L) else null,
            )
        }
        render("diet-long", DietUiState(rows = rows, earlier = earlier, caloriesSoFar = "~1,850 kcal so far", loaded = true))
        compose.assertClutterBudget()
    }

    @Test fun calorieGoalBar() {
        val rows = listOf(
            entry("t1", "8:10", "Oatmeal").copy(kcalLabel = "300 kcal"),
            entry("t2", "11:27", "Banana").copy(kcalLabel = "≈ 105 kcal"),
            entry("t3", "13:10", "Salad"),
        )
        val state = DietUiState(
            rows = rows, loaded = true, showCalories = true, caloriesSoFar = "≈ 405 kcal so far",
            calories = app.klean.feature.diet.ui.DayCalories(405, estimated = true, missing = 1), calorieGoal = 2000,
            withoutCalories = "1 food without calories",
        )
        render("diet-calorie-goal", state)
        compose.onNodeWithText("≈ 405 of 2,000 kcal").assertExists()
        compose.onNodeWithText("≈ 405 kcal so far", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 food without calories", substring = true).assertExists()
        compose.assertClutterBudget()
    }

    @Test fun noBarWithoutAGoal() {
        val state = DietUiState(
            rows = listOf(entry("t1", "8:10", "Oatmeal").copy(kcalLabel = "300 kcal")), loaded = true, showCalories = true,
            caloriesSoFar = "300 kcal so far", calories = app.klean.feature.diet.ui.DayCalories(300, false, 0),
        )
        compose.setContent { DietScreenContent(state = state, onOpenEntry = {}, onOpenHistory = {}, onAddForGap = {}) }
        compose.onNodeWithText("of", substring = true).assertDoesNotExist()
        compose.onNodeWithText("300 kcal so far", substring = true).assertExists()
    }

    @Test fun fillInCaloriesIsInTheMenuWhenSomeAreMissing() {
        var filled = 0
        val state = DietUiState(rows = listOf(entry("t1", "8:10", "Salad")), loaded = true, showCalories = true, withoutCalories = "1 food without calories")
        compose.setContent { DietScreenContent(state = state, onOpenEntry = {}, onOpenHistory = {}, onAddForGap = {}, onFillCalories = { filled++ }) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Fill in calories").performClick()
        assertEquals(1, filled)
    }

    @Test fun copyTodayIsInTheMenu() {
        var copied = 0
        val state = DietUiState(rows = listOf(entry("t1", "8:10", "Salad")), loaded = true)
        compose.setContent { DietScreenContent(state = state, onOpenEntry = {}, onOpenHistory = {}, onAddForGap = {}, onCopyToday = { copied++ }) }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Fill in calories").assertDoesNotExist()
        compose.onNodeWithText("Copy today").performClick()
        assertEquals(1, copied)
    }

    @Test fun historyIsInTheOverflow() {
        val shot = render("diet-menu", DietUiState(rows = listOf(entry("t1", "8:10", "Oatmeal, banana")), loaded = true))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("History").assertExists()
        shot.capture("diet-menu-open")
    }
}
