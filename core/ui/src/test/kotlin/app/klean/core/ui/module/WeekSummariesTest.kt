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

class WeekSummariesTest {
    private class Gate : ModuleGate {
        val ids = MutableStateFlow(emptySet<ModuleId>())
        override val disabledIds: Flow<Set<ModuleId>> = ids
        override suspend fun isEnabled(id: ModuleId): Boolean = id !in ids.value
        override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {
            ids.value = if (enabled) ids.value - id else ids.value + id
        }
    }

    private val days = (5..11).map { LocalDate(2026, 11, it) }

    private fun provider(id: ModuleId, fact: String?) = object : WeekSummaryProvider {
        override val moduleId = id
        override fun week(days: List<LocalDate>) = flowOf(fact?.let { WeekSummary(id, id.value, "$it ${days.size}") })
    }

    @Test fun enabledTrackersOnlyAndQuietOnesLeftOut() = runTest {
        val gate = Gate()
        val w = WeekSummaries(setOf(provider(ModuleId.WORKOUT, "2"), provider(ModuleId.SLEEP, "7h"), provider(ModuleId.DIET, null)), gate)
        assertEquals(listOf("7h 7", "2 7"), w.week(days).first().map { it.fact })
        gate.setEnabled(ModuleId.SLEEP, false)
        assertEquals(listOf(ModuleId.WORKOUT), w.week(days).first().map { it.moduleId })
    }

    @Test fun noProvidersIsAnEmptyWeek() = runTest {
        assertEquals(emptyList<WeekSummary>(), WeekSummaries(emptySet(), Gate()).week(days).first())
    }
}
