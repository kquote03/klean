package app.klean.feature.calendar.mirror

import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import app.klean.core.data.db.entity.EventEntity
import app.klean.feature.calendar.DAY
import app.klean.feature.calendar.FakeModuleGate
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.event
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KleanCalendarMirrorTest {
    private class FakeProvider : CalendarProviderOps {
        var writable = true
        val calendars = mutableMapOf<Long, Map<String, Any?>>()
        val events = mutableMapOf<Long, Map<String, Any?>>()
        private var next = 100L
        override fun canWrite() = writable
        override fun findKleanCalendar(): Long? = calendars.keys.firstOrNull()
        override fun createCalendar(values: Map<String, Any?>): Long = next++.also { calendars[it] = values }
        override fun deleteCalendar(id: Long) {
            calendars.remove(id)
            events.entries.removeAll { it.value[Events.CALENDAR_ID] == id }
        }
        override fun syncIds(calendarId: Long): Map<String, Long> =
            events.filterValues { it[Events.CALENDAR_ID] == calendarId }.entries.associate { it.value[Events._SYNC_ID] as String to it.key }
        override fun insertEvent(values: Map<String, Any?>): Long = next++.also { events[it] = values }
        override fun updateEvent(id: Long, values: Map<String, Any?>) { events[id] = values }
        override fun deleteEvent(id: Long) { events.remove(id) }
    }

    private val provider = FakeProvider()
    private val store = mutableMapOf<String, EventEntity>()
    private val gate = FakeModuleGate()
    private val mirror = KleanCalendarMirror(
        provider, MirrorSource({ store[it] }, { store.values.toList() }), gate,
        accountName = "Klean", accountType = "app.klean.debug", ensureAccount = {},
    )

    @Test fun timedEventValues() {
        val start = dubai(2026, 11, 13, 9, 30)
        val v = MirrorValues.event(event("e1", "Dentist", start).copy(location = "Clinic", notes = "Bring form"), 5)
        assertEquals(5L, v[Events.CALENDAR_ID])
        assertEquals("Dentist", v[Events.TITLE])
        assertEquals("Clinic", v[Events.EVENT_LOCATION])
        assertEquals("Bring form", v[Events.DESCRIPTION])
        assertEquals(start, v[Events.DTSTART])
        assertEquals(start + HOUR, v[Events.DTEND])
        assertEquals(0, v[Events.ALL_DAY])
        assertEquals("Asia/Dubai", v[Events.EVENT_TIMEZONE])
        assertEquals("e1", v[Events._SYNC_ID])
        assertEquals("Klean reminds, so the system calendar must not", 0, v[Events.HAS_ALARM])
        assertNull(v[Events.RRULE])
    }

    @Test fun allDayEventsUseUtcMidnight() {
        val v = MirrorValues.event(event("e2", "Holiday", dubai(2026, 12, 2), dubai(2026, 12, 4), allDay = true), 5)
        assertEquals(1, v[Events.ALL_DAY])
        assertEquals("UTC", v[Events.EVENT_TIMEZONE])
        assertEquals(1_796_169_600_000L, v[Events.DTSTART]) // 2026-12-02T00:00Z
        assertEquals(1_796_169_600_000L + 2 * DAY, v[Events.DTEND])
    }

    @Test fun recurringEventsUseDurationAndExdates() {
        val start = dubai(2026, 11, 13, 9)
        val ex = start + 7 * DAY
        val v = MirrorValues.event(event("e3", "Class", start, rrule = "FREQ=WEEKLY", exdates = "[$ex]"), 5)
        assertEquals("FREQ=WEEKLY", v[Events.RRULE])
        assertEquals("P3600S", v[Events.DURATION])
        assertNull(v[Events.DTEND])
        assertEquals("20261120T050000Z", v[Events.EXDATE])
    }

    @Test fun calendarValuesAreReadOnlyUnderTheKleanAccount() {
        val v = MirrorValues.calendar("Klean", "app.klean.debug", "Asia/Dubai")
        assertEquals("Klean", v[Calendars.ACCOUNT_NAME])
        assertEquals("app.klean.debug", v[Calendars.ACCOUNT_TYPE])
        assertEquals("Klean", v[Calendars.CALENDAR_DISPLAY_NAME])
        assertEquals(Calendars.CAL_ACCESS_READ, v[Calendars.CALENDAR_ACCESS_LEVEL])
        assertEquals(1, v[Calendars.VISIBLE])
    }

    @Test fun changesInsertUpdateAndDeleteRows() = runBlocking {
        store["a"] = event("a", "One", dubai(2026, 11, 13, 9))
        mirror.onEventsChanged(listOf("a"))
        assertEquals(1, provider.calendars.size)
        assertEquals("One", provider.events.values.single()[Events.TITLE])

        store["a"] = store["a"]!!.copy(title = "One, moved")
        mirror.onEventsChanged(listOf("a"))
        assertEquals("One, moved", provider.events.values.single()[Events.TITLE])

        store["a"] = store["a"]!!.copy(sync = store["a"]!!.sync.copy(deleted = true))
        mirror.onEventsChanged(listOf("a"))
        assertTrue(provider.events.isEmpty())
    }

    @Test fun fullResyncRemovesOrphansAndOnlyMirrorsKleanEvents() = runBlocking {
        store["a"] = event("a", "Mine", dubai(2026, 11, 13, 9))
        store["b"] = event("b", "Elsewhere", dubai(2026, 11, 13, 9)).copy(calendarId = "google-1")
        mirror.reschedule()
        val cal = provider.calendars.keys.single()
        provider.insertEvent(mapOf(Events.CALENDAR_ID to cal, Events._SYNC_ID to "gone"))
        mirror.reschedule()
        assertEquals(listOf("a"), provider.events.values.map { it[Events._SYNC_ID] })
    }

    @Test fun nothingIsWrittenWithoutPermissionAndTurningOffRemovesTheCalendar() = runBlocking {
        provider.writable = false
        store["a"] = event("a", "One", dubai(2026, 11, 13, 9))
        mirror.reschedule()
        assertTrue(provider.calendars.isEmpty())

        provider.writable = true
        mirror.reschedule()
        assertEquals(1, provider.events.size)
        gate.setEnabled(ModuleId.CALENDAR, false)
        mirror.reschedule()
        assertTrue(provider.calendars.isEmpty())
        assertTrue(provider.events.isEmpty())
    }
}
