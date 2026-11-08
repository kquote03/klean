package app.klean.shared.diet.prompt

import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.MINUTE_MS
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FormatRotatorTest {
    private fun h(format: PromptFormat, outcome: PromptOutcome, phrasing: String = "p") = PromptHistoryItem(format, phrasing, outcome)

    @Test fun emptyHistory_standard() = assertEquals(PromptFormat.STANDARD, FormatRotator.nextFormat(emptyList()))
    @Test fun lastLogged_keepsFormat() =
        assertEquals(PromptFormat.HEADS_UP, FormatRotator.nextFormat(listOf(h(PromptFormat.HEADS_UP, PromptOutcome.LOGGED))))
    @Test fun ignoredStandard_headsUp() =
        assertEquals(PromptFormat.HEADS_UP, FormatRotator.nextFormat(listOf(h(PromptFormat.STANDARD, PromptOutcome.IGNORED))))
    @Test fun ignoredWidgetHighlight_wrapsToStandard() =
        assertEquals(PromptFormat.STANDARD, FormatRotator.nextFormat(listOf(h(PromptFormat.WIDGET_HIGHLIGHT, PromptOutcome.IGNORED))))
    @Test fun dismissedSilent_widgetHighlight() =
        assertEquals(PromptFormat.WIDGET_HIGHLIGHT, FormatRotator.nextFormat(listOf(h(PromptFormat.SILENT_VIBRATE, PromptOutcome.DISMISSED))))
    @Test fun pendingSkipped() = assertEquals(
        PromptFormat.HEADS_UP,
        FormatRotator.nextFormat(listOf(h(PromptFormat.SILENT_VIBRATE, PromptOutcome.PENDING), h(PromptFormat.HEADS_UP, PromptOutcome.LOGGED))),
    )
    @Test fun onlyPending_standard() =
        assertEquals(PromptFormat.STANDARD, FormatRotator.nextFormat(listOf(h(PromptFormat.HEADS_UP, PromptOutcome.PENDING))))

    @Test fun phrasing_avoidsLastThree() {
        val pool = listOf("a", "b", "c", "d", "e")
        val history = listOf("a", "b", "c", "d").map { h(PromptFormat.STANDARD, PromptOutcome.IGNORED, it) }
        val random = Random(42)
        repeat(200) {
            val p = FormatRotator.nextPhrasing(pool, history, random)
            assertFalse(p in setOf("a", "b", "c"), p)
        }
    }
    @Test fun phrasing_singlePool() = assertEquals(
        "only",
        FormatRotator.nextPhrasing(listOf("only"), listOf(h(PromptFormat.STANDARD, PromptOutcome.LOGGED, "only")), Random(1)),
    )

    private val sent = 1_762_600_000_000L
    @Test fun resolver_loggedWithinWindow() =
        assertEquals(PromptOutcome.LOGGED, PromptOutcomeResolver.resolve(sent, listOf(sent + 30 * MINUTE_MS), sent + 3 * HOUR_MS))
    @Test fun resolver_ignoredAfterTwoHours() =
        assertEquals(PromptOutcome.IGNORED, PromptOutcomeResolver.resolve(sent, emptyList(), sent + 2 * HOUR_MS))
    @Test fun resolver_pendingAfterOneHour() =
        assertEquals(PromptOutcome.PENDING, PromptOutcomeResolver.resolve(sent, emptyList(), sent + HOUR_MS))
}
