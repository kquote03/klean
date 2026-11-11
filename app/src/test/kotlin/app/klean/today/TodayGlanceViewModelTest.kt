package app.klean.today

import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.TodayGlance
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayGlanceViewModelTest {
    // 2026-11-11 (Wednesday) 10:00 in Dubai = 06:00 UTC
    private val now = Instant.parse("2026-11-11T06:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val zone = object : ZoneProvider { override fun current() = TimeZone.of("Asia/Dubai") }
    private val gate = FakeModuleGate()
    private val checked = mutableListOf<String>()
    private fun provider(id: ModuleId, c: GlanceContribution) = object : TodayGlanceProvider {
        override val moduleId = id
        override fun glance() = flowOf(c)
        override suspend fun check(row: GlanceRow) { checked += row.key }
    }
    private val ms = now.toEpochMilliseconds()
    private val diet = provider(ModuleId.DIET, GlanceContribution(done = listOf(GlanceRow("diet:1", ModuleId.DIET, "shawarma", ms - 3_600_000))))
    private val tasks = provider(ModuleId.TASKS, GlanceContribution(upcoming = listOf(GlanceRow("task:1", ModuleId.TASKS, "call bank", ms + 3_600_000, checkable = true))))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(visible: MutableStateFlow<List<ModuleId>>) =
        TodayGlanceViewModel(TodayGlance(setOf(diet, tasks), gate), visible, clock, zone, flowOf(Unit))

    @Test fun dateAndTheMergedDay() = runTest {
        val s = vm(MutableStateFlow(listOf(ModuleId.TASKS, ModuleId.DIET))).state.first { it.loaded }
        assertEquals(LocalDate(2026, 11, 11), s.today)
        assertEquals(listOf("shawarma"), s.glance.done.rows.map { it.text })
        assertEquals(listOf("call bank"), s.glance.upcoming.rows.map { it.text })
    }

    @Test fun hidingOrSwitchingOffAModuleRemovesItsRows() = runTest {
        val visible = MutableStateFlow(listOf(ModuleId.TASKS, ModuleId.DIET))
        val vm = vm(visible)
        vm.state.first { it.loaded }
        visible.value = listOf(ModuleId.TASKS)
        assertTrue(vm.state.first { it.glance.done.rows.isEmpty() }.glance.upcoming.rows.isNotEmpty())
        gate.state.value = setOf(ModuleId.TASKS)
        assertTrue(vm.state.first { it.glance.upcoming.rows.isEmpty() }.glance.done.rows.isEmpty())
    }

    @Test fun tickingARowReachesItsModule() = runTest {
        val vm = vm(MutableStateFlow(listOf(ModuleId.TASKS)))
        vm.check(vm.state.first { it.loaded }.glance.upcoming.rows.single())
        assertEquals(listOf("task:1"), checked)
    }
}
