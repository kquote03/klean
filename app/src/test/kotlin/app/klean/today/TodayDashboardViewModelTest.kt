package app.klean.today

import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.core.ui.module.TodayItems
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.nav.FakeModuleGate
import app.klean.nav.FakeScreenModule
import app.klean.nav.FakeRoute1
import app.klean.nav.NavGroup
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayDashboardViewModelTest {
    // 2026-11-11 (Wednesday) 10:00 in Dubai = 06:00 UTC
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-11T06:00:00Z") }
    private val zone = object : ZoneProvider { override fun current() = TimeZone.of("Asia/Dubai") }
    private val gate = FakeModuleGate()
    private val sleep = object : DashboardSummaryProvider {
        override val moduleId = ModuleId.SLEEP
        override fun summaries() = flowOf(listOf(SummaryItem("sleep", "Sleep", "Last night · 7h 10m")))
    }
    private val calendar = object : TodayItemsProvider {
        override val moduleId = ModuleId.CALENDAR
        override fun items() = flowOf(listOf(TodayItem("event:1", "15:00", "Team sync", 1L, "klean://calendar")))
    }
    private fun module(id: ModuleId): KleanModule = FakeScreenModule(id, 1, android.R.string.ok, FakeRoute1) {}

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(visible: MutableStateFlow<List<KleanModule>>) = TodayDashboardViewModel(
        DashboardContributions(setOf(sleep), emptySet(), gate),
        TodayItems(setOf(calendar), gate),
        visible,
        clock, zone, flowOf(Unit),
    )

    @Test fun dateAndOneCardPerGroup() = runTest {
        val visible = MutableStateFlow(listOf(module(ModuleId.CALENDAR), module(ModuleId.SLEEP)))
        val s = vm(visible).state.first { it.loaded }
        assertEquals(LocalDate(2026, 11, 11), s.today)
        assertEquals(listOf(NavGroup.PLAN, NavGroup.TRACK), s.cards.map { it.group })
        assertEquals("Team sync", s.cards[0].fact)
        assertEquals("15:00", s.cards[0].supporting)
        assertEquals("Last night · 7h 10m", s.cards[1].fact)
    }

    @Test fun hidingAModuleRemovesItsCard() = runTest {
        val visible = MutableStateFlow(listOf(module(ModuleId.CALENDAR), module(ModuleId.SLEEP)))
        val vm = vm(visible)
        vm.state.first { it.loaded }
        visible.value = listOf(module(ModuleId.CALENDAR))
        assertEquals(listOf(NavGroup.PLAN), vm.state.first { it.cards.size == 1 }.cards.map { it.group })
    }

    @Test fun aModuleSwitchedOffAddsNothing() = runTest {
        gate.state.value = setOf(ModuleId.SLEEP)
        val visible = MutableStateFlow(listOf(module(ModuleId.CALENDAR), module(ModuleId.SLEEP)))
        assertEquals(listOf(NavGroup.PLAN), vm(visible).state.first { it.loaded }.cards.map { it.group })
    }
}
