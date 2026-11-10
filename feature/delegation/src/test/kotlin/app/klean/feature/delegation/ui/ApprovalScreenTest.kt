package app.klean.feature.delegation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h1600dp")
class ApprovalScreenTest {
    @get:Rule val rule = createComposeRule()
    private val six = (1..6).map { "p$it@example.com" }

    @Test fun showsTheWholeDraftAndEveryRecipientAndApprovesEdits() {
        var decided: ApprovalUi? = null
        var rejected = false
        rule.setContent {
            var s by remember { mutableStateOf(ApprovalUi(true, "Invite the team", "send_email", "Hi all", six, "Hi all", six)) }
            KleanTheme {
                ApprovalContent(
                    s,
                    onDraft = { s = s.copy(draft = it) },
                    onRecipient = { i, v -> s = s.copy(recipients = s.recipients.toMutableList().also { l -> l[i] = v }) },
                    onRemove = { i -> s = s.copy(recipients = s.recipients.filterIndexed { j, _ -> j != i }) },
                    onAdd = { s = s.copy(recipients = s.recipients + it) },
                    onApprove = { decided = s },
                    onReject = { rejected = true },
                )
            }
        }
        rule.onNodeWithText("Hi all").assertIsDisplayed()
        six.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("6 recipients").assertIsDisplayed()
        rule.onNodeWithText("more than 5 people", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Approve").assertIsDisplayed()

        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithContentDescription("Remove p6@example.com").performClick()
        rule.onNodeWithText("5 recipients").assertIsDisplayed()
        rule.onNodeWithText("Approve with edits").performClick()
        assertEquals(six.take(5), decided!!.recipients)
        assertEquals(true, decided!!.edited)
        assertEquals(false, rejected)
    }
}
