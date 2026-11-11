package app.klean.feature.contacts.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.date
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** People › Contacts, its Birthdays view and a contact in light and dark, each within the clutter budget. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PeopleShotsTest {
    @get:Rule val compose = createComposeRule()

    private fun row(id: String, name: String, detail: String? = null) = ContactRow("klean:$id", name, detail, contactId = id)

    private val people = listOf(
        row("l", "Lina Saeed", "050 123 4567"), row("m", "Maya Rahman"), row("o", "Omar Khalil", "omar@studionine.example"),
        row("s", "Sara Haddad", "050 765 4321"), row("y", "Yusuf Ali"),
    )
    private val birthdays = BirthdaySections(
        comingUp = listOf(BirthdayRow("s", "Sara Haddad", date(2026, 11, 13), 3, 35), BirthdayRow("y", "Yusuf Ali", date(2026, 12, 2), 22, null)),
        byMonth = listOf(3 to listOf(BirthdayRow("m", "Maya Rahman", date(2027, 3, 3), 113, 29))),
    )
    private val typical = ContactsUiState(
        loaded = true, canReadPhone = true, today = date(2026, 11, 10),
        sections = listOf(ContactSection("all", people)), birthdays = birthdays,
    )

    private fun render(name: String, s: ContactsUiState, tab: String? = null, onOpen: (String?, Long?) -> Unit = { _, _ -> }) =
        compose.shot(name, screen = true) { ContactsContent(s, tab, {}, onOpen, {}, {}, {}, {}, {}, {}) }

    @Test fun typical() {
        val opened = mutableListOf<String?>()
        render("people-typical", typical, onOpen = { id, _ -> opened += id })
        compose.onNodeWithText("Coming up").assertExists()
        compose.onNodeWithText("Everyone").assertExists()
        compose.onNodeWithText("Omar Khalil").performClick()
        assertEquals(listOf<String?>("o"), opened)
        compose.assertClutterBudget()
    }

    @Test fun birthdaysView() {
        render("people-birthdays", typical, tab = "birthdays")
        compose.onNodeWithText("All people").assertExists()
        compose.onNodeWithText("March").assertExists()
        compose.assertClutterBudget()
        compose.onNodeWithText("All people").performClick()
        compose.onNodeWithText("Everyone").assertExists()
    }

    @Test fun heroOpensBirthdays() {
        compose.setContent { ContactsContent(typical, null, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Turns 35 · Nov 13").performClick()
        compose.onNodeWithText("All people").assertExists()
    }

    @Test fun empty() {
        render("people-empty", ContactsUiState(loaded = true, canReadPhone = true))
        compose.onNodeWithText("New contact").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun longWithOtherAccountAndAccess() {
        val many = (1..30).map { row("p$it", "Person with a fairly long name number $it", "050 000 00$it") }
        render("people-long", typical.copy(sections = listOf(ContactSection("all", many)), other = listOf(ContactSection("me@dav", people.take(2))), canReadPhone = false))
        compose.assertClutterBudget()
    }

    @Test fun searchOpensAField() {
        val shot = render("people-search", typical)
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Search names, numbers, emails").assertExists()
        shot.capture("people-search-open")
        compose.assertClutterBudget(maxTextInputs = 1)
    }

    @Test fun contactDetail() {
        val detail = ContactDetail(
            name = "Sara Haddad", org = "Studio Nine",
            phones = listOf(LabeledValue("mobile", "050 765 4321")), emails = listOf(LabeledValue("work", "sara@studionine.example")),
            birthday = Birthday(11, 13, 1991), nextBirthday = date(2026, 11, 13), turns = 35, reminderDays = listOf(0, 1),
            notes = "Prefers mornings for calls.",
        )
        var called: String? = null
        compose.shot("contact-detail", screen = true) {
            ContactDetailContent(ContactDetailUiState(loaded = true, contact = detail, contactId = "s"), {}, {}, onCall = { called = it }, onEmail = {})
        }
        compose.onNodeWithText("050 765 4321").performClick()
        assertEquals("050 765 4321", called)
        compose.onNodeWithContentDescription("Edit contact").assertExists()
        compose.assertClutterBudget()
    }
}
