package app.klean.widget

import app.klean.core.ui.module.TodayItem
import app.klean.core.ui.module.TodayItems
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWidgetStateTest {
    private fun item(key: String, at: Long) = TodayItem(key, timeLabel = "t$at", text = key, sortKey = at, route = "klean://$key")
    private fun provider(id: ModuleId, vararg items: TodayItem) = object : TodayItemsProvider {
        override val moduleId = id
        override fun items() = flowOf(items.toList())
    }

    @Test fun rowsAreSoonestFirstAndCappedAtFive() = runTest {
        val gate = FakeModuleGate()
        val items = TodayItems(
            setOf(
                provider(ModuleId.TASKS, item("t1", 30), item("t2", 10), item("t3", 70)),
                provider(ModuleId.CALENDAR, item("c1", 20), item("c2", 60), item("c3", 40)),
            ),
            gate,
        )
        val s = todayWidgetState(items.items().first())
        assertEquals(listOf("t2", "c1", "t1", "c3", "c2"), s.rows.map { it.key })
        assertEquals(6, s.total)
    }

    @Test fun nothingComingUpIsTheEmptyState() {
        val s = todayWidgetState(emptyList())
        assertTrue(s.isEmpty)
        assertEquals(0, s.total)
    }

    @Test fun disabledModulesAreAbsent() = runTest {
        val gate = FakeModuleGate(setOf(ModuleId.CALENDAR))
        val items = TodayItems(
            setOf(provider(ModuleId.TASKS, item("t1", 30)), provider(ModuleId.CALENDAR, item("c1", 20))),
            gate,
        )
        assertEquals(listOf("t1"), todayWidgetState(items.items().first()).rows.map { it.key })
        gate.setEnabled(ModuleId.TASKS, false)
        assertTrue(todayWidgetState(items.items().first()).isEmpty)
    }
}
