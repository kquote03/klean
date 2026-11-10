package app.klean.feature.calendar.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeAgenda
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.FakeDeviceAccess
import app.klean.feature.calendar.date
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    // Tuesday 10 Nov 2026.
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val agenda = FakeAgenda()
    private val device = FakeDeviceAccess()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun vm(state: Map<String, Any?> = emptyMap()) =
        CalendarViewModel(SavedStateHandle(state), agenda, device, clock, DubaiZone).also { store.put("vm${store.keys().size}", it) }

    @Test fun rangesFollowTheView() {
        val tue = date(2026, 11, 10)
        assertEquals(tue to date(2026, 11, 23), CalendarRange.of(CalendarView.AGENDA, tue))
        assertEquals(tue to tue, CalendarRange.of(CalendarView.DAY, tue))
        assertEquals(tue to date(2026, 11, 12), CalendarRange.of(CalendarView.THREE_DAY, tue))
        assertEquals(date(2026, 11, 9) to date(2026, 11, 15), CalendarRange.of(CalendarView.WEEK, tue))
        // Month grid: whole weeks, Monday first, covering 1–30 November.
        assertEquals(date(2026, 10, 26) to date(2026, 12, 6), CalendarRange.of(CalendarView.MONTH, tue))
    }

    @Test fun startsOnTheAgendaForTodayAndGroupsByDay() = runTest {
        agenda.all.value = listOf(item("a", "Dentist", dubai(2026, 11, 13, 9)), item("b", "Gym", dubai(2026, 11, 10, 18)))
        vm().state.test {
            var s = awaitItem()
            while (!s.loaded) s = awaitItem()
            assertEquals(CalendarView.AGENDA, s.view)
            assertEquals(date(2026, 11, 10), s.anchor)
            assertEquals(listOf(date(2026, 11, 10), date(2026, 11, 13)), s.byDay.keys.toList())
            assertEquals(14, s.days.size)
            assertFalse(s.canReadDevice)
        }
    }

    @Test fun aDayRouteOpensThatDay() = runTest {
        val vm = vm(mapOf("isoDate" to "2026-11-20"))
        vm.state.test {
            var s = awaitItem()
            while (!s.loaded) s = awaitItem()
            assertEquals(CalendarView.DAY, s.view)
            assertEquals(listOf(date(2026, 11, 20)), s.days)
        }
    }

    @Test fun nextPreviousTodayAndOpenDayMoveTheAnchor() {
        val vm = vm()
        vm.setView(CalendarView.WEEK)
        vm.next()
        assertEquals(date(2026, 11, 17), vm.state.value.anchor)
        vm.previous()
        vm.previous()
        assertEquals(date(2026, 11, 3), vm.state.value.anchor)
        vm.setView(CalendarView.MONTH)
        vm.next()
        assertEquals(date(2026, 12, 3), vm.state.value.anchor)
        vm.openDay(date(2026, 12, 24))
        assertEquals(CalendarView.DAY, vm.state.value.view)
        assertEquals(date(2026, 12, 24), vm.state.value.anchor)
        vm.goToday()
        assertEquals(date(2026, 11, 10), vm.state.value.anchor)
    }

    @Test fun grantingAccessReadsThePhoneCalendars() = runTest {
        val vm = vm()
        device.granted = true
        vm.onAccessResult()
        assertEquals(1, device.changes)
        assertTrue(vm.state.value.canReadDevice)
    }
}
