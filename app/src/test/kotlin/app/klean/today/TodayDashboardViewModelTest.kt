package app.klean.today

import app.klean.core.common.ZoneProvider
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.SummaryItem
import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import java.time.DayOfWeek
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
class TodayDashboardViewModelTest {
    // 2026-11-11 (Wednesday) 10:00 in Dubai = 06:00 UTC
    private var now = Instant.parse("2026-11-11T06:00:00Z")
    private val clock = object : Clock { override fun now() = this@TodayDashboardViewModelTest.now }
    private val dubai = TimeZone.of("Asia/Dubai")
    private val zone = object : ZoneProvider { override fun current() = dubai }
    private val gate = FakeModuleGate()
    private val dietSummaries = MutableStateFlow(listOf(SummaryItem("diet.today", "Today", "3 entries")))
    private val summary = object : DashboardSummaryProvider {
        override val moduleId = ModuleId.DIET
        override fun summaries() = dietSummaries
    }
    private val asked = mutableListOf<Pair<LocalDate, LocalDate>>()
    private val activity = object : DayActivityProvider {
        override val moduleId = ModuleId.DIET
        override fun activity(from: LocalDate, to: LocalDate) =
            flowOf(mapOf(LocalDate(2026, 11, 10) to 4)).also { asked += from to to }
        override fun dayRoute(date: LocalDate): Any = "day:$date"
    }
    private fun vm(ticks: kotlinx.coroutines.flow.Flow<Unit> = flowOf(Unit)) = TodayDashboardViewModel(
        DashboardContributions(setOf(summary), setOf(activity), gate), clock, zone, DayOfWeek.MONDAY, ticks,
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun headerSummariesAndWeek() = runTest {
        val s = vm().state.first { it.loaded }
        assertEquals(Greeting.MORNING, s.greeting)
        assertEquals(LocalDate(2026, 11, 11), s.today)
        assertEquals(listOf("diet.today"), s.summaries.map { it.key })
        assertTrue(s.showWeek)
        assertEquals(LocalDate(2026, 11, 9), s.week.first().date)
        assertEquals(7, s.week.size)
        assertEquals(4, s.week.first { it.date == LocalDate(2026, 11, 10) }.count)
        assertEquals("day:2026-11-10", s.week.first { it.date == LocalDate(2026, 11, 10) }.route)
        assertEquals(0, s.week.first().count)
        assertTrue(s.week.first { it.date == LocalDate(2026, 11, 11) }.isToday)
        assertTrue(s.week.last().isFuture)
        assertEquals(false, s.canGoNext)
    }

    @Test fun previousWeekAndBackButNeverAhead() = runTest {
        val vm = vm()
        vm.state.first { it.loaded }
        vm.previousWeek()
        val prev = vm.state.first { it.loaded && it.weekOffset == -1 }
        assertEquals(LocalDate(2026, 11, 2), prev.week.first().date)
        assertTrue(prev.canGoNext)
        assertTrue(prev.week.none { it.isToday || it.isFuture })
        vm.nextWeek(); vm.nextWeek()
        assertEquals(0, vm.state.first { it.loaded }.weekOffset)
    }

    @Test fun disablingTheModuleClearsItsTilesAndWeek() = runTest {
        val vm = vm()
        vm.state.first { it.loaded && it.summaries.isNotEmpty() }
        gate.setEnabled(ModuleId.DIET, false)
        val s = vm.state.first { it.summaries.isEmpty() }
        assertEquals(false, s.showWeek)
    }

    @Test fun greetingAndDayFollowTheClockWithoutReloadingTheWeekEachMinute() = runTest {
        val ticks = MutableSharedFlow<Unit>(replay = 1)
        ticks.tryEmit(Unit)
        val vm = vm(ticks)
        vm.state.first { it.loaded }
        now = Instant.parse("2026-11-11T09:30:00Z") // 13:30 in Dubai
        ticks.emit(Unit)
        assertEquals(Greeting.AFTERNOON, vm.state.first { it.greeting == Greeting.AFTERNOON }.greeting)
        assertEquals(1, asked.size)
        now = Instant.parse("2026-11-15T20:30:00Z") // Monday 16 Nov, 00:30 in Dubai: a new week
        ticks.emit(Unit)
        val s = vm.state.first { it.today == LocalDate(2026, 11, 16) }
        assertEquals(Greeting.NIGHT, s.greeting)
        assertEquals(LocalDate(2026, 11, 16), s.week.first().date)
    }
}
