package app.klean.feature.calendar.agenda

import app.klean.core.data.db.entity.CalendarSourceEntity
import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeCalendarRepository
import app.klean.feature.calendar.FakeModuleGate
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.data.SourceKind
import app.klean.feature.calendar.date
import app.klean.feature.calendar.device.DeviceInstance
import app.klean.feature.calendar.device.DeviceInstances
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.event
import app.klean.feature.calendar.sync
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAgendaTest {
    private val repo = FakeCalendarRepository()
    private val gate = FakeModuleGate()
    private val showTasks = MutableStateFlow(true)
    private val showReminders = MutableStateFlow(true)
    private val deviceItems = MutableStateFlow(emptyList<DeviceInstance>())
    private val device = object : DeviceInstances {
        override fun instances(fromMs: Long, toMs: Long): Flow<List<DeviceInstance>> = deviceItems
        override suspend fun sourceId(calendarId: Long) = "device-$calendarId"
    }
    private val tasks = object : DayItemsProvider {
        override val moduleId = ModuleId.TASKS
        override fun items(from: LocalDate, to: LocalDate) = flowOf(
            listOf(DayItem("task:1", date(2026, 11, 11), "Pay rent", minutes = null, kind = DayItemKind.TASK)),
        )
    }
    private val reminders = object : DayItemsProvider {
        override val moduleId = ModuleId.REMINDERS
        override fun items(from: LocalDate, to: LocalDate) = flowOf(
            listOf(DayItem("reminder:1", date(2026, 11, 11), "Stretch", minutes = 17 * 60, kind = DayItemKind.REMINDER)),
        )
    }
    private val agenda = CalendarAgenda(repo, device, setOf(tasks, reminders), gate, { showTasks }, { showReminders }, DubaiZone)

    private val from = date(2026, 11, 10)
    private val to = date(2026, 11, 16)

    @Test fun mergesEventsDeviceItemsTasksAndRemindersInDayOrder() = runBlocking {
        repo.events.value = listOf(
            event("gym", "Gym", dubai(2026, 11, 11, 18), rrule = "FREQ=DAILY;COUNT=2"),
            event("hol", "Holiday", dubai(2026, 11, 11), dubai(2026, 11, 12), allDay = true),
        )
        deviceItems.value = listOf(
            DeviceInstance(1, 7, "Team call", dubai(2026, 11, 11, 10), dubai(2026, 11, 11, 11), false, "Work", 0xFF00FF00.toInt()),
        )
        val items = agenda.items(from, to).first()
        val nov11 = items.filter { date(2026, 11, 11) in it.firstDay..it.lastDay }
        assertEquals(listOf("Holiday", "Pay rent", "Team call", "Stretch", "Gym"), nov11.map { it.title })
        assertEquals(AgendaKind.DEVICE, nov11[2].kind)
        assertEquals("Work", nov11[2].sourceName)
        assertTrue(nov11[2].readOnly)
        assertEquals("gym", nov11[4].eventId)
        // The second gym occurrence is on the 12th.
        assertEquals(2, items.count { it.title == "Gym" })
        assertEquals(date(2026, 11, 12), items.last { it.title == "Gym" }.firstDay)
    }

    @Test fun hiddenSourcesSettingsAndDisabledModulesDropTheirItems() = runBlocking {
        repo.events.value = listOf(event("a", "Mine", dubai(2026, 11, 11, 9)))
        deviceItems.value = listOf(DeviceInstance(1, 7, "Team call", dubai(2026, 11, 11, 10), dubai(2026, 11, 11, 11), false, "Work", null))
        repo.sources.value = listOf(
            CalendarSourceEntity("device-7", SourceKind.DEVICE, "Work", remoteId = "7", readOnly = true, selected = false, sync = sync()),
            CalendarSourceEntity(repo.kleanSourceId, SourceKind.KLEAN, "Klean", selected = false, sync = sync()),
        )
        showReminders.value = false
        gate.setEnabled(ModuleId.TASKS, false)
        assertEquals(emptyList<String>(), agenda.items(from, to).first().map { it.title })
    }

    @Test fun theEventSnapshotHasOnlyEventsAndHonoursSelection() = runBlocking {
        repo.events.value = listOf(event("a", "Mine", dubai(2026, 11, 11, 9)))
        deviceItems.value = listOf(
            DeviceInstance(1, 7, "Team call", dubai(2026, 11, 11, 10), dubai(2026, 11, 11, 11), false, "Work", null),
            DeviceInstance(2, 8, "Hidden", dubai(2026, 11, 11, 12), dubai(2026, 11, 11, 13), false, "Other", null),
        )
        repo.sources.value = listOf(CalendarSourceEntity("device-8", SourceKind.DEVICE, "Other", remoteId = "8", selected = false, sync = sync()))
        assertEquals(listOf("Mine", "Team call"), agenda.eventSnapshot(from, to).map { it.title })
    }

    @Test fun deviceAllDayItemsAreReadInUtc() = runBlocking {
        val utcMidnight = 1_796_169_600_000L // 2026-12-02T00:00Z
        deviceItems.value = listOf(DeviceInstance(2, 7, "National Day", utcMidnight, utcMidnight + 2 * DAY, true, "Holidays", null))
        val item = agenda.items(date(2026, 12, 1), date(2026, 12, 5)).first().single()
        assertTrue(item.allDay)
        assertEquals(date(2026, 12, 2), item.firstDay)
        assertEquals(date(2026, 12, 3), item.lastDay)
    }

    @Test fun aTimedEventOverMidnightSpansTwoDays() = runBlocking {
        repo.events.value = listOf(event("n", "Night flight", dubai(2026, 11, 12, 23), dubai(2026, 11, 13, 3)))
        val item = agenda.items(from, to).first().single { it.kind == AgendaKind.EVENT }
        assertEquals(date(2026, 11, 12), item.firstDay)
        assertEquals(date(2026, 11, 13), item.lastDay)
        assertEquals(4 * HOUR, item.end - item.start)
    }
}
