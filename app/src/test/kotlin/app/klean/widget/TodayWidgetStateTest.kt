package app.klean.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.InstantResult
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import app.klean.today.buildGlance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home-screen Today widget is a small whiteboard: one-tap actions, what's next, and the day so far. */
class TodayWidgetStateTest {
    private fun row(key: String, m: ModuleId, text: String, at: Long) = GlanceRow(key, m, text, at)
    private val icon = Icons.Rounded.Star
    private val parts = mapOf(
        ModuleId.DIET to GlanceContribution(
            done = listOf(row("d1", ModuleId.DIET, "coffee with milk", 810), row("d2", ModuleId.DIET, "shawarma", 1315)),
            quickAction = QuickAction.Capture(ModuleId.DIET, "Food", icon),
        ),
        ModuleId.TASKS to GlanceContribution(
            upcoming = listOf(row("t1", ModuleId.TASKS, "call bank", 1500), row("t2", ModuleId.TASKS, "bins", 1600), row("t3", ModuleId.TASKS, "gym bag", 1700)),
            done = listOf(row("t0", ModuleId.TASKS, "paid rent", 900)),
            quickAction = QuickAction.Capture(ModuleId.TASKS, "Task", icon),
        ),
        ModuleId.SLEEP to GlanceContribution(
            done = listOf(row("s1", ModuleId.SLEEP, "Slept 7 h 20 m", 700)),
            quickAction = QuickAction.Instant(ModuleId.SLEEP, "Bedtime", icon) { InstantResult("Good night", null) },
        ),
        ModuleId.WORKOUT to GlanceContribution(quickAction = QuickAction.Open(ModuleId.WORKOUT, "Workout", icon, Unit)),
    )
    private val all = listOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP, ModuleId.WORKOUT)

    @Test fun showsTheFoodsEatenNotACount() {
        val s = todayWidgetState(buildGlance(all, parts, now = 1200))
        assertEquals(listOf("paid rent", "shawarma"), s.done.takeLast(2).map { it.text })
        assertTrue(s.done.any { it.text == "shawarma" })
        assertEquals(3, s.done.size)
        assertEquals(listOf("coffee with milk", "paid rent", "shawarma"), s.done.map { it.text })
    }

    @Test fun nextTwoComingUp() {
        val s = todayWidgetState(buildGlance(all, parts, now = 1200))
        assertEquals(listOf("call bank", "bins"), s.upcoming.map { it.text })
    }

    @Test fun speakFoodTaskAndSleepAsOneTapActions() {
        val s = todayWidgetState(buildGlance(all, parts, now = 1200))
        assertEquals(listOf(WidgetActionKind.VOICE, WidgetActionKind.CAPTURE, WidgetActionKind.CAPTURE, WidgetActionKind.SLEEP), s.actions.map { it.kind })
        assertEquals(listOf(null, ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP), s.actions.map { it.module })
        assertEquals("Bedtime", s.actions.last().label)
    }

    @Test fun modulesSwitchedOffDropTheirActionsAndRows() = runTest {
        val gate = FakeModuleGate(setOf(ModuleId.DIET, ModuleId.SLEEP))
        val glance = TodayGlance(
            parts.map { (id, c) ->
                object : TodayGlanceProvider {
                    override val moduleId = id
                    override fun glance() = flowOf(c)
                }
            }.toSet(),
            gate,
        )
        val on = glance.byModule().first()
        val s = todayWidgetState(buildGlance(on.keys.toList(), on, now = 1200))
        assertEquals(listOf(null, ModuleId.TASKS), s.actions.map { it.module })
        assertTrue(s.done.none { it.module == ModuleId.DIET })
    }

    @Test fun nothingYetIsTheEmptyState() {
        val s = todayWidgetState(buildGlance(emptyList(), emptyMap(), now = 0))
        assertTrue(s.isEmpty)
        assertEquals(listOf(WidgetActionKind.VOICE), s.actions.map { it.kind })
    }
}
