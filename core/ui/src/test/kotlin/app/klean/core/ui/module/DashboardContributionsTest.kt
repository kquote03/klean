package app.klean.core.ui.module

import app.klean.core.data.module.ModuleGate
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardContributionsTest {
    private class Gate(off: Set<ModuleId> = emptySet()) : ModuleGate {
        val ids = MutableStateFlow(off)
        override val disabledIds: Flow<Set<ModuleId>> = ids
        override suspend fun isEnabled(id: ModuleId): Boolean = id !in ids.value
        override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
            ids.value = if (enabled) ids.value - id else ids.value + id
        }
    }
    private val d1 = LocalDate(2026, 11, 9)

    private fun summary(id: ModuleId, key: String) = object : DashboardSummaryProvider {
        override val moduleId = id
        override fun summaries() = flowOf(listOf(SummaryItem(key = key, label = key, value = "v")))
    }
    private fun activity(id: ModuleId, n: Int) = object : DayActivityProvider {
        override val moduleId = id
        override fun activity(from: LocalDate, to: LocalDate) = flowOf(mapOf(d1 to n))
        override fun dayRoute(date: LocalDate): Any = "route-${id.value}"
    }

    @Test fun summariesFromDisabledModulesAreHidden() = runTest {
        val gate = Gate()
        val c = DashboardContributions(
            setOf(summary(ModuleId.DIET, "diet"), summary(ModuleId.CORE, "core")), emptySet(), gate,
        )
        assertEquals(listOf("core", "diet"), c.summaries().first().map { it.key })
        gate.setEnabled(ModuleId.DIET, false)
        assertEquals(listOf("core"), c.summaries().first().map { it.key })
    }

    @Test fun activityIsSummedPerDayAndRoutedToTheFirstModuleWithActivity() = runTest {
        val c = DashboardContributions(
            emptySet(), setOf(activity(ModuleId.DIET, 3), activity(ModuleId.WORKOUT, 2)), Gate(),
        )
        val week = c.activity(d1, d1).first()
        assertEquals(5, week.getValue(d1).count)
        assertEquals("route-diet", week.getValue(d1).route)
    }

    @Test fun noActivityProvidersMeansNoWeek() = runTest {
        val gate = Gate()
        val c = DashboardContributions(emptySet(), setOf(activity(ModuleId.DIET, 3)), gate)
        assertEquals(true, c.hasActivityProviders().first())
        gate.setEnabled(ModuleId.DIET, false)
        assertEquals(false, c.hasActivityProviders().first())
        assertEquals(emptyMap<LocalDate, DayActivity>(), c.activity(d1, d1).first())
    }
}
