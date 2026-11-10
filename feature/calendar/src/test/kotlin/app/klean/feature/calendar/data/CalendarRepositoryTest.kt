package app.klean.feature.calendar.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.HOUR
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.inMemoryDb
import app.klean.feature.calendar.tempDevicePrefs
import app.klean.shared.ical.ICalCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {
    private val now = dubai(2026, 11, 10, 12)
    private val clock = FakeClock(now)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val changed = mutableListOf<List<String>>()
    private val repo = DefaultCalendarRepository(
        db, stamper, clock, DubaiZone, prefs,
        setOf(EventChangeListener { synchronized(changed) { changed += it } }),
    )

    private val draft = EventDraft(
        title = "  Dentist ", startAt = dubai(2026, 11, 13, 9, 30), endAt = dubai(2026, 11, 13, 10, 30),
        zone = "Asia/Dubai", location = "Clinic", reminderMinutes = 10,
    )

    @After fun close() = db.close()

    @Test fun addStoresATrimmedEventWithSyncColumnsAndTellsListeners() = runBlocking {
        val id = repo.add(draft)
        val e = db.eventDao().get(id)!!
        assertEquals("Dentist", e.title)
        assertEquals(draft.startAt, e.startAt)
        assertEquals(id, e.uid)
        assertNull("Klean's own calendar", e.calendarId)
        assertEquals(now, e.sync.createdAt)
        assertTrue(e.sync.changeSeq > 0)
        assertEquals(listOf(listOf(id)), changed)
    }

    @Test fun updateKeepsCreatedAtAndAdvancesChangeSeq() = runBlocking {
        val id = repo.add(draft)
        val before = db.eventDao().get(id)!!
        clock.nowMs += HOUR
        repo.update(id, draft.copy(title = "Dentist, Dr. Haddad", rrule = "FREQ=MONTHLY"))
        val after = db.eventDao().get(id)!!
        assertEquals("Dentist, Dr. Haddad", after.title)
        assertEquals("FREQ=MONTHLY", after.rrule)
        assertEquals(before.sync.createdAt, after.sync.createdAt)
        assertEquals(now + HOUR, after.sync.updatedAt)
        assertTrue(after.sync.changeSeq > before.sync.changeSeq)
    }

    @Test fun deleteTombstones() = runBlocking {
        val id = repo.add(draft)
        repo.delete(id)
        val e = db.eventDao().get(id)!!
        assertTrue(e.sync.deleted)
        assertEquals(emptyList<Any>(), repo.observeEvents().first())
        assertEquals(2, changed.size)
    }

    @Test fun skippingOneOccurrenceAddsAnExdate() = runBlocking {
        val id = repo.add(draft.copy(rrule = "FREQ=WEEKLY"))
        val second = draft.startAt + 7 * 24 * HOUR
        repo.skipOccurrence(id, second)
        assertEquals(listOf(second), Exdates.decode(db.eventDao().get(id)!!.exdates))
    }

    @Test fun blankTitlesAreRefused() = runBlocking {
        val r = runCatching { repo.add(draft.copy(title = "  ")) }
        assertTrue(r.isFailure)
    }

    @Test fun icsExportThenImportUpdatesInsteadOfDuplicating() = runBlocking {
        val id = repo.add(draft.copy(rrule = "FREQ=WEEKLY;COUNT=4"))
        repo.skipOccurrence(id, draft.startAt + 7 * 24 * HOUR)
        val ics = repo.exportIcs()
        val parsed = ICalCodec.parse(ics).single()
        assertEquals(id, parsed.uid)
        assertEquals("FREQ=WEEKLY;COUNT=4", parsed.rrule)
        assertEquals(1, parsed.exdates.size)
        assertEquals(10, parsed.alarmMinutesBefore)

        val result = repo.importIcs(ics.replace("SUMMARY:Dentist", "SUMMARY:Dentist moved"))
        assertEquals(ImportResult(added = 0, updated = 1), result)
        val events = repo.observeEvents().first()
        assertEquals(1, events.size)
        assertEquals("Dentist moved", events.single().title)
    }

    @Test fun icsImportAddsNewEventsWithTheirUid() = runBlocking {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:holiday-1@example.com
            DTSTART;VALUE=DATE:20261202
            DTEND;VALUE=DATE:20261204
            SUMMARY:National Day
            END:VEVENT
            BEGIN:VEVENT
            UID:no-start
            SUMMARY:Broken
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        assertEquals(ImportResult(added = 1, updated = 0), repo.importIcs(text))
        val e = repo.observeEvents().first().single()
        assertEquals("holiday-1@example.com", e.uid)
        assertTrue(e.allDay)
        assertEquals(dubai(2026, 12, 2), e.startAt)
        assertEquals(dubai(2026, 12, 4), e.endAt)
    }

    @Test fun kleanSourceIsCreatedOnceAndCanBeHidden() = runBlocking {
        val a = repo.ensureKleanSource()
        val b = repo.ensureKleanSource()
        assertEquals(a.id, b.id)
        assertEquals(SourceKind.KLEAN, a.kind)
        repo.setSourceSelected(a.id, false)
        assertFalse(repo.observeSources().first().single().selected)
    }

    @Test fun deviceSourcesAreUpsertedOnlyWhenTheyChange() = runBlocking {
        val cals = listOf(DeviceCalendar(7, "Work", 0xFF336699.toInt(), "work@example.com"))
        repo.syncDeviceSources(cals)
        val first = repo.observeSources().first().single()
        assertEquals(SourceKind.DEVICE, first.kind)
        assertEquals("7", first.remoteId)
        assertTrue(first.readOnly)
        repo.syncDeviceSources(cals)
        assertEquals(first.sync.changeSeq, repo.observeSources().first().single().sync.changeSeq)
        repo.setSourceSelected(first.id, false)
        repo.syncDeviceSources(listOf(cals.single().copy(name = "Work (new)")))
        val renamed = repo.observeSources().first().single()
        assertEquals("Work (new)", renamed.name)
        assertFalse("the user's choice survives a rename", renamed.selected)
        assertEquals(first.id, repo.deviceSourceId(7))
    }
}
