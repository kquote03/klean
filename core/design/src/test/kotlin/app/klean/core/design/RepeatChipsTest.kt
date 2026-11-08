package app.klean.core.design

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.RepeatChips
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepeatChipsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eachChipSaysLogAgainAndLogsOnOneTap() {
        val tapped = mutableListOf<String>()
        compose.setContent { KleanTheme { RepeatChips(listOf("tea", "toast"), onRepeat = { tapped += it }) } }
        compose.onNodeWithContentDescription("Log tea again").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Log toast again").performClick()
        assertEquals(listOf("tea", "toast"), tapped)
    }
}
