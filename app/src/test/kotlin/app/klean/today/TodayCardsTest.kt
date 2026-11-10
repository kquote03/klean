package app.klean.today

import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.nav.NavGroup
import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayCardsTest {
    private val all = listOf(
        ModuleId.TASKS, ModuleId.CALENDAR, ModuleId.BIRTHDAYS, ModuleId.SLEEP, ModuleId.WORKOUT, ModuleId.DIET, ModuleId.REMINDERS,
    )
    private fun tile(key: String, label: String, value: String) = SummaryItem(key, label, value)
    private fun item(key: String, text: String, time: String?, at: Long) = TodayItem(key, time, text, at, "klean://x")

    private val summaries = mapOf(
        ModuleId.TASKS to listOf(tile("tasks", "Tasks", "2 for today")),
        ModuleId.SLEEP to listOf(tile("sleep", "Sleep", "Last night · 7h 10m")),
        ModuleId.WORKOUT to listOf(tile("workout", "Workout", "Last on Thursday")),
        ModuleId.DIET to listOf(tile("diet.today", "Today", "2 entries"), tile("diet.last", "Last entry", "Oatmeal, banana · 8:10")),
        ModuleId.BIRTHDAYS to listOf(tile("bday", "Next birthday", "Sara, in 3 days")),
    )
    private val items = mapOf(
        ModuleId.CALENDAR to listOf(item("event:1", "Team sync", "15:00", 15), item("event:2", "Dinner with Lina", "19:30", 19)),
        ModuleId.TASKS to listOf(item("task:1", "Buy groceries", null, 0)),
        ModuleId.REMINDERS to listOf(item("reminder:1", "Call the bank", "16:00", 16)),
    )

    @Test fun oneCardPerGroupInArrangedOrder() {
        val cards = todayCards(all, summaries, items)
        assertEquals(listOf(NavGroup.PLAN, NavGroup.PEOPLE, NavGroup.TRACK), cards.map { it.group })
    }

    @Test fun planLeadsWithTheSoonestDatedItemAndListsTheNextThree() {
        val plan = todayCards(all, summaries, items).first { it.group == NavGroup.PLAN }
        assertNull(plan.label)
        assertEquals("Buy groceries", plan.fact)
        assertEquals(listOf("Team sync", "Call the bank", "Dinner with Lina"), plan.rows.map { it.text })
        assertEquals(listOf(false, false, false), plan.rows.map { it.isTask })
    }

    @Test fun planWithNothingDatedIsCalmAndShowsTheTasksLine() {
        val plan = todayCards(all, summaries, emptyMap()).first { it.group == NavGroup.PLAN }
        assertNull(plan.fact)
        assertEquals(listOf("2 for today"), plan.rows.map { it.text })
    }

    @Test fun trackLeadsWithSleepThenOneLinePerModule() {
        val track = todayCards(all, summaries, items).first { it.group == NavGroup.TRACK }
        assertEquals("Sleep", track.label)
        assertEquals("Last night · 7h 10m", track.fact)
        assertEquals(listOf("2 entries", "Last on Thursday", "Oatmeal, banana · 8:10"), track.rows.map { it.text })
    }

    @Test fun hiddenOrOffModulesAddNothingAndAnEmptyGroupHasNoCard() {
        val cards = todayCards(listOf(ModuleId.TASKS, ModuleId.WORKOUT), summaries, items)
        assertEquals(listOf(NavGroup.PLAN, NavGroup.TRACK), cards.map { it.group })
        assertEquals("Buy groceries", cards[0].fact)
        assertEquals(emptyList<String>(), cards[0].rows.map { it.text })
        assertEquals("Last on Thursday", cards[1].fact)
        assertEquals(emptyList<TodayCard>(), todayCards(listOf(ModuleId.CONTACTS), emptyMap(), emptyMap()))
    }

    @Test fun rowsAreCappedAtThree() {
        val many = mapOf(ModuleId.TASKS to (1..9).map { item("task:$it", "Task $it", null, it.toLong()) })
        val plan = todayCards(listOf(ModuleId.TASKS), emptyMap(), many).single()
        assertEquals(MAX_ROWS, plan.rows.size)
        assertEquals(true, plan.rows.all { it.isTask })
    }
}
