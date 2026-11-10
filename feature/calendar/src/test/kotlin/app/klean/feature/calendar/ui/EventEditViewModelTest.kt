package app.klean.feature.calendar.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeCalendarRepository
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.FakePrefs
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.calendar.data.Exdates
import app.klean.feature.calendar.data.Repeat
import app.klean.feature.calendar.date
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.ui.edit.EventEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditViewModelTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12, 20))
    private val repo = FakeCalendarRepository()
    private val prefs = FakePrefs()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun vm(state: Map<String, Any?> = emptyMap()) =
        EventEditViewModel(SavedStateHandle(state), repo, prefs, clock, DubaiZone).also { store.put("vm${store.keys().size}", it) }

    @Test fun aNewEventStartsAtTheNextHourForOneHourWithTheDefaultReminder() {
        val s = vm().state.value
        assertTrue(s.isNew)
        assertEquals(date(2026, 11, 10), s.form.startDate)
        assertEquals(13 * 60, s.form.startMinutes)
        assertEquals(14 * 60, s.form.endMinutes)
        assertEquals(10, s.form.reminder)
        assertFalse(s.canSave)
    }

    @Test fun aNewEventOnAnotherDayStartsAtNine() {
        val s = vm(mapOf("isoDate" to "2026-11-20")).state.value
        assertEquals(date(2026, 11, 20), s.form.startDate)
        assertEquals(9 * 60, s.form.startMinutes)
    }

    @Test fun savingANewTimedEvent() = runBlocking {
        val vm = vm()
        vm.setTitle("Dentist")
        vm.setStartDate(date(2026, 11, 13))
        vm.setStartTime(9 * 60 + 30)
        vm.setRepeat(Repeat.WEEKLY)
        vm.setReminder(60)
        vm.save()
        assertTrue(vm.state.value.done)
        val e = repo.events.value.single()
        assertEquals("Dentist", e.title)
        assertEquals(dubai(2026, 11, 13, 9, 30), e.startAt)
        assertEquals(dubai(2026, 11, 13, 10, 30), e.endAt)
        assertEquals("FREQ=WEEKLY", e.rrule)
        assertEquals(60, e.reminderMinutes)
    }

    @Test fun movingTheStartKeepsTheDurationAndEndNeverPrecedesStart() {
        val vm = vm()
        vm.setEndTime(15 * 60) // 13:00–15:00
        vm.setStartTime(16 * 60)
        assertEquals(18 * 60, vm.state.value.form.endMinutes)
        vm.setEndDate(date(2026, 11, 9))
        assertEquals(date(2026, 11, 10), vm.state.value.form.endDate)
    }

    @Test fun anAllDayEventCoversWholeDaysWithNoReminder() = runBlocking {
        val vm = vm()
        vm.setTitle("Holiday")
        vm.setAllDay(true)
        vm.setEndDate(date(2026, 11, 11))
        vm.save()
        val e = repo.events.value.single()
        assertTrue(e.allDay)
        assertEquals(dubai(2026, 11, 10), e.startAt)
        assertEquals(dubai(2026, 11, 12), e.endAt)
        assertNull(e.reminderMinutes)
    }

    @Test fun editingLoadsTheEventAndSavesInPlace() = runBlocking {
        val id = repo.add(
            EventDraft("Class", dubai(2026, 11, 12, 18), dubai(2026, 11, 12, 19), zone = "Asia/Dubai", rrule = "FREQ=WEEKLY;BYDAY=TH", location = "Studio"),
        )
        val vm = vm(mapOf("eventId" to id, "occurrenceStart" to dubai(2026, 11, 19, 18)))
        val s = vm.state.value
        assertFalse(s.isNew)
        assertEquals("Class", s.form.title)
        assertEquals("Studio", s.form.location)
        assertEquals(Repeat.CUSTOM, s.form.repeat)
        assertTrue(s.recurring)
        vm.setTitle("Pottery class")
        vm.save()
        assertEquals("Pottery class", repo.events.value.single().title)
        assertEquals("FREQ=WEEKLY;BYDAY=TH", repo.events.value.single().rrule)
    }

    @Test fun deletingOneOccurrenceOrTheWholeSeries() = runBlocking {
        val id = repo.add(EventDraft("Class", dubai(2026, 11, 12, 18), dubai(2026, 11, 12, 19), zone = "Asia/Dubai", rrule = "FREQ=WEEKLY"))
        val second = dubai(2026, 11, 12, 18) + 7 * DAY
        val vm = vm(mapOf("eventId" to id, "occurrenceStart" to second))
        vm.deleteThis()
        assertEquals(listOf(second), Exdates.decode(repo.events.value.single().exdates))
        assertTrue(vm.state.value.done)
        vm(mapOf("eventId" to id)).deleteAll()
        assertTrue(repo.events.value.single().sync.deleted)
    }

    @Test fun startTimeIsKeptWhenSwitchingAllDayOff() {
        val vm = vm()
        vm.setAllDay(true)
        vm.setAllDay(false)
        assertEquals(13 * 60, vm.state.value.form.startMinutes)
        assertEquals(HOUR / 60_000, (vm.state.value.form.endMinutes - vm.state.value.form.startMinutes).toLong())
    }
}
