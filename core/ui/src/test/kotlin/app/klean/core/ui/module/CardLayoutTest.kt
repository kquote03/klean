package app.klean.core.ui.module

import org.junit.Assert.assertEquals
import org.junit.Test

class CardLayoutTest {
    private data class Card(val id: String, val slot: Int)
    private val approvals = Card("approvals", 0)
    private val tasks = Card("tasks", 1)
    private val sleep = Card("sleep", 4)
    private val diet = Card("diet", 6)
    private val all = listOf(diet, sleep, tasks, approvals)

    private fun arrange(saved: List<String> = emptyList(), hidden: Set<String> = emptySet()) =
        arrangeCards(all, { it.id }, { it.slot }, saved, hidden).map { it.id }

    @Test fun defaultOrderFollowsTheSlots() =
        assertEquals(listOf("approvals", "tasks", "sleep", "diet"), arrange())

    @Test fun savedOrderWins() =
        assertEquals(listOf("diet", "sleep", "tasks", "approvals"), arrange(saved = listOf("diet", "sleep", "tasks", "approvals")))

    @Test fun unknownNewModuleLandsAfterTheSavedOnesInSlotOrder() =
        assertEquals(listOf("diet", "tasks", "approvals", "sleep"), arrange(saved = listOf("diet", "tasks")))

    @Test fun hiddenCardsAreRemoved() =
        assertEquals(listOf("tasks", "sleep"), arrange(hidden = setOf("diet", "approvals")))
}
