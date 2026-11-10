package app.klean.shared.delegation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApprovalPolicyTest {
    private fun people(n: Int) = (1..n).map { "p$it@example.com" }

    @Test fun moreThanFiveRecipientsAlwaysNeedApprovalInEveryMode() {
        listOf("PARTIAL", "INTERACTIVE", "AGENTIC").forEach { mode ->
            assertTrue(ApprovalPolicy.needsApproval(mode, "send_email", people(6)), mode)
        }
    }

    @Test fun duplicatesAndBlanksDontCount() {
        assertFalse(ApprovalPolicy.needsApproval("AGENTIC", "send_email", people(5) + people(5) + listOf(" ", "P1@example.com")))
    }

    @Test fun interactiveAsksForEverythingAgenticOnlyForManyRecipients() {
        assertTrue(ApprovalPolicy.needsApproval("INTERACTIVE", "send_email", people(1)))
        assertFalse(ApprovalPolicy.needsApproval("AGENTIC", "send_email", people(5)))
        assertFalse(ApprovalPolicy.needsApproval("AGENTIC", "book_table", emptyList()))
    }
}

class KleanBlocksTest {
    @Test fun findsBlocksAnywhereIncludingCodeFences() {
        val text = """
            I need one detail first.
            ```json
            {"klean":"question","text":"Which address should I use for Khalid?"}
            ```
        """.trimIndent()
        assertEquals(listOf(KleanBlock.Question("Which address should I use for Khalid?")), KleanBlocks.parse(text))
        assertEquals("I need one detail first.", KleanBlocks.strip(text))
    }

    @Test fun approvalCarriesDraftAndRecipients() {
        val text = """Here is the draft. {"klean":"approval","action":"send_email","draft":"Hi all, {see} below","recipients":["a@x.com","b@x.com"]}"""
        assertEquals(
            listOf(KleanBlock.Approval("send_email", "Hi all, {see} below", listOf("a@x.com", "b@x.com"))),
            KleanBlocks.parse(text),
        )
    }

    @Test fun ordinaryJsonAndBrokenBlocksAreIgnored() {
        assertEquals(emptyList(), KleanBlocks.parse("""Result: {"total": 3} and {"klean":"question" broken"""))
    }

    @Test fun repliesAreKleanBlocksToo() {
        assertEquals(listOf(KleanBlock.Answer("tomorrow")), KleanBlocks.parse(KleanBlocks.answer("tomorrow")))
        val d = KleanBlocks.parse(KleanBlocks.decision("EDITED", "Hi", listOf("a@x.com")))
        assertEquals(listOf(KleanBlock.Decision("EDITED", "Hi", listOf("a@x.com"))), d)
    }

    @Test fun delegateTitleDropsTheAskPrefix() {
        assertEquals("Book a table for Friday", KleanBlocks.delegateTitle("ask Hermes to book a table for Friday"))
        assertEquals("Email the team about the offsite", KleanBlocks.delegateTitle("Hermes, email the team about the offsite"))
        assertEquals("Find a plumber", KleanBlocks.delegateTitle("delegate: find a plumber"))
    }
}
