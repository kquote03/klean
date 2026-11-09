package app.klean.core.design

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.SummaryTile
import app.klean.core.design.component.WeekDayCell
import app.klean.core.design.component.WeekOverview
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tileShowsLabelAndValueAndIsATallEnoughTarget() {
        var taps = 0
        compose.setContent { KleanTheme { SummaryTile("Today", "3 entries", null, onClick = { taps++ }) } }
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("3 entries").assertHeightIsAtLeast(16.dp)
        compose.onNodeWithContentDescription("Today, 3 entries").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }

    @Test fun emptyDaysAreBlankAndEveryDayHasALabel() {
        val days = listOf(
            WeekDayCell(key = "a", weekday = "M", dayNumber = "9", count = 0, isToday = false, isFuture = false, description = "Monday 9 November, nothing logged"),
            WeekDayCell(key = "b", weekday = "T", dayNumber = "10", count = 2, isToday = true, isFuture = false, description = "Tuesday 10 November, 2 things logged"),
            WeekDayCell(key = "c", weekday = "W", dayNumber = "11", count = 0, isToday = false, isFuture = true, description = "Wednesday 11 November, nothing logged"),
        )
        var opened: String? = null
        compose.setContent {
            KleanTheme {
                WeekOverview(
                    title = "This week", days = days, onDay = { opened = it.key },
                    onPrevious = {}, onNext = {}, canGoNext = false,
                    previousDescription = "Previous week", nextDescription = "Next week",
                )
            }
        }
        compose.onNodeWithContentDescription("Monday 9 November, nothing logged").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Tuesday 10 November, 2 things logged").performClick()
        assertEquals("b", opened)
        compose.onNodeWithContentDescription("Wednesday 11 November, nothing logged").performClick()
        assertEquals("b", opened) // future days don't open
        compose.onNodeWithText("0").assertDoesNotExist() // no zero badges, ever
        compose.onNodeWithContentDescription("Next week").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous week").assertHeightIsAtLeast(48.dp)
    }
}
