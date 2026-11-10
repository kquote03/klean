package app.klean.feature.contacts.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.contacts.date
import app.klean.feature.contacts.merge.DuplicatePair
import app.klean.feature.contacts.merge.Person
import app.klean.feature.contacts.today.BirthdaysCardContent
import app.klean.feature.contacts.today.BirthdaysCardState
import app.klean.feature.contacts.today.UpcomingBirthday
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ContactsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val pair = DuplicatePair(
        Person("klean:a", "Sara", emptyList(), emptyList(), kleanId = "a"),
        Person("raw:3", "Sara", emptyList(), emptyList(), rawId = 3, accountName = "me@dav"),
    )
    private val state = ContactsUiState(
        loaded = true,
        sections = listOf(ContactSection("S", listOf(ContactRow("klean:a", "Sara", "050 123", contactId = "a")))),
        other = listOf(ContactSection("me@dav", listOf(ContactRow("raw:3", "Sara Dav", null, rawId = 3)))),
        mergePairs = listOf(pair),
        birthdays = BirthdaySections(comingUp = listOf(BirthdayRow("a", "Sara", date(2026, 11, 11), 1, 30))),
    )

    private fun show(s: ContactsUiState, tab: String? = null, onOpen: (String?, Long?) -> Unit = { _, _ -> }, merged: MutableList<DuplicatePair> = mutableListOf()) =
        rule.setContent {
            ContactsContent(s, tab, {}, onOpen, {}, {}, {}, { merged += it }, {}, {})
        }

    @Test fun peopleListMergeCardAndOtherAccount() {
        val opened = mutableListOf<Pair<String?, Long?>>()
        val merged = mutableListOf<DuplicatePair>()
        show(state, onOpen = { a, b -> opened += a to b }, merged = merged)
        rule.onNodeWithText("These look like the same people").assertIsDisplayed()
        rule.onNodeWithText("Sara, also in me@dav").assertIsDisplayed()
        rule.onNodeWithText("Merge").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(pair), merged)
        rule.onNodeWithText("In me@dav").assertIsDisplayed()
        rule.onNodeWithText("Sara Dav").performClick()
        assertEquals(listOf<Pair<String?, Long?>>(null to 3L), opened)
        rule.onNodeWithContentDescription("New contact").assertIsDisplayed()
        rule.onNodeWithContentDescription("Contacts settings").assertHeightIsAtLeast(48.dp)
    }

    @Test fun birthdaysTabFromTheDeepLinkAndHiddenWhenOff() {
        show(state, tab = "birthdays")
        rule.onNodeWithText("Coming up").assertIsDisplayed()
        rule.onNodeWithText("Tomorrow").assertIsDisplayed()
        rule.onNodeWithText("Turns 30").assertIsDisplayed()
    }

    @Test fun noBirthdaysTabWithTheModuleOff() {
        show(state.copy(birthdaysOn = false), tab = "birthdays")
        assertEquals(0, rule.onAllNodesWithText("Birthdays").fetchSemanticsNodes().size)
        rule.onNodeWithText("Sara Dav").assertIsDisplayed()
    }

    @Test fun todayCardIsCalmWhenEmpty() {
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                BirthdaysCardContent(BirthdaysCardState(true, emptyList()), {})
                BirthdaysCardContent(BirthdaysCardState(true, listOf(UpcomingBirthday("a", "Omar", date(2026, 11, 10), 0, null))), {})
            }
        }
        rule.onNodeWithText("No birthdays in the next 30 days.").assertIsDisplayed()
        rule.onNodeWithText("Omar").assertIsDisplayed()
        rule.onNodeWithText("Today").assertIsDisplayed()
    }
}
