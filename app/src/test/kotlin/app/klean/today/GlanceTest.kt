package app.klean.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Today's whiteboard model: rows ordered by time across modules, no module first by rank, bounded sections. */
class GlanceTest {
    private fun hm(h: Int, m: Int = 0) = (h * 60L + m) * 60_000L
    private fun row(key: String, module: ModuleId, at: Long?) = GlanceRow(key, module, key, at)
    private fun capture(m: ModuleId) = QuickAction.Capture(m, m.value, Icons.Rounded.Star)

    @Test fun doneIsTimeOrderedAcrossModules() {
        val parts = mapOf(
            ModuleId.DIET to GlanceContribution(done = listOf(row("oats", ModuleId.DIET, hm(8, 10)))),
            ModuleId.TASKS to GlanceContribution(done = listOf(row("call bank", ModuleId.TASKS, hm(9)))),
            ModuleId.SLEEP to GlanceContribution(done = listOf(row("slept", ModuleId.SLEEP, hm(7)))),
        )
        val g = buildGlance(listOf(ModuleId.TASKS, ModuleId.DIET, ModuleId.SLEEP), parts, now = hm(12))
        assertEquals(listOf("slept", "oats", "call bank"), g.done.rows.map { it.key })
    }

    @Test fun noModuleIsAHero() {
        val parts = mapOf(
            ModuleId.DIET to GlanceContribution(done = listOf(row("lunch", ModuleId.DIET, hm(13)), row("coffee", ModuleId.DIET, hm(8)))),
            ModuleId.WORKOUT to GlanceContribution(done = listOf(row("run", ModuleId.WORKOUT, hm(7)))),
        )
        val g = buildGlance(listOf(ModuleId.DIET, ModuleId.WORKOUT), parts, now = hm(14))
        // The model is four plain sections; nothing in it ranks one module above another.
        val fields = Glance::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
        assertEquals(setOf("quickActions", "attention", "upcoming", "done"), fields.toSet())
        assertEquals("run", g.done.rows.first().key)
        assertEquals(listOf(hm(7), hm(8), hm(13)), g.done.rows.map { it.at })
    }

    @Test fun upcomingDropsPastItems() {
        val parts = mapOf(
            ModuleId.CALENDAR to GlanceContribution(
                upcoming = listOf(row("standup", ModuleId.CALENDAR, hm(10)), row("dentist", ModuleId.CALENDAR, hm(15))),
            ),
            ModuleId.TASKS to GlanceContribution(upcoming = listOf(row("bins", ModuleId.TASKS, null))),
        )
        val g = buildGlance(listOf(ModuleId.CALENDAR, ModuleId.TASKS), parts, now = hm(12))
        assertEquals(listOf("dentist", "bins"), g.upcoming.rows.map { it.key })
    }

    @Test fun quickActionsFollowArrangeOrderAndCapAtEight() {
        val nine = listOf(
            ModuleId.WORKOUT, ModuleId.TASKS, ModuleId.DIET, ModuleId.SLEEP, ModuleId.CALENDAR,
            ModuleId.REMINDERS, ModuleId.CONTACTS, ModuleId.BIRTHDAYS, ModuleId.DELEGATION,
        )
        val parts = nine.associateWith { GlanceContribution(quickAction = capture(it)) }
        val g = buildGlance(nine, parts, now = 0)
        // Speak and Type take two of the grid's eight tiles (added by the screen), so modules get at most six.
        assertTrue(g.quickActions.size <= MAX_QUICK_MODULE_ACTIONS)
        assertEquals(nine.take(MAX_QUICK_MODULE_ACTIONS), g.quickActions.map { it.module })
        assertEquals(8, MAX_QUICK_MODULE_ACTIONS + 2)
    }

    @Test fun hiddenModulesContributeNothing() = runTest {
        val parts = mapOf(
            ModuleId.DIET to GlanceContribution(done = listOf(row("oats", ModuleId.DIET, hm(8))), quickAction = capture(ModuleId.DIET)),
            ModuleId.SLEEP to GlanceContribution(done = listOf(row("slept", ModuleId.SLEEP, hm(7))), quickAction = capture(ModuleId.SLEEP)),
        )
        // Hidden in Arrange Today: not in the visible list.
        val g = buildGlance(listOf(ModuleId.DIET), parts, now = hm(12))
        assertEquals(listOf("oats"), g.done.rows.map { it.key })
        assertEquals(listOf(ModuleId.DIET), g.quickActions.map { it.module })

        // Switched off: the gate drops the provider before Today sees it.
        val gate = FakeModuleGate(setOf(ModuleId.SLEEP))
        val glance = TodayGlance(
            parts.map { (id, c) ->
                object : TodayGlanceProvider {
                    override val moduleId = id
                    override fun glance() = flowOf(c)
                }
            }.toSet(),
            gate,
        )
        val byModule = glance.byModule().first()
        assertEquals(setOf(ModuleId.DIET), byModule.keys)
        val off = buildGlance(listOf(ModuleId.DIET, ModuleId.SLEEP), byModule, now = hm(12))
        assertFalse(off.done.rows.any { it.module == ModuleId.SLEEP })
    }

    @Test fun attentionKeepsAtMostFive() {
        val rows = (1..8).map { row("a$it", ModuleId.TASKS, hm(it)) }
        val g = buildGlance(listOf(ModuleId.TASKS), mapOf(ModuleId.TASKS to GlanceContribution(attention = rows)), now = 0)
        assertEquals(MAX_ATTENTION, g.attention.rows.size)
        assertEquals(3, g.attention.moreCount)
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), g.attention.rows.map { it.key })
        // The day so far is never cut short.
        val many = (1..20).map { row("f$it", ModuleId.DIET, hm(1, it)) }
        val done = buildGlance(listOf(ModuleId.DIET), mapOf(ModuleId.DIET to GlanceContribution(done = many)), now = 0).done
        assertEquals(20, done.rows.size)
        assertEquals(0, done.moreCount)
    }
}
