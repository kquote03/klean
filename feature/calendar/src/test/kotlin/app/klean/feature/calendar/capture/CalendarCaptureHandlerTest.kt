package app.klean.feature.calendar.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.calendar.CalendarKeys
import app.klean.feature.calendar.DubaiZone
import app.klean.feature.calendar.FakeClock
import app.klean.feature.calendar.data.DefaultCalendarRepository
import app.klean.feature.calendar.dubai
import app.klean.feature.calendar.inMemoryDb
import app.klean.feature.calendar.tempDevicePrefs
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarCaptureHandlerTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12, 20))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = DefaultCalendarRepository(db, stamper, clock, DubaiZone, tempDevicePrefs(clock), emptySet())
    private val handler = CalendarCaptureHandler(repo, settings, clock, DubaiZone)

    @After fun close() = db.close()

    private fun input(text: String) = CaptureInput(text, CaptureSource.NOTIFICATION_REPLY, clock.nowMs)

    @Test fun savesAtOnceWithTheDefaultReminderAndUndoTombstones() = runBlocking {
        assertEquals(ModuleId.CALENDAR, handler.moduleId)
        assertEquals(listOf(RuleCaptureClassifier.ADD_EVENT), handler.actionSpecs.map { it.type })
        val id = handler.execute(input("dentist friday 9:30"), handler.defaultAction(input("x")))
        val e = db.eventDao().get(id)!!
        assertEquals("dentist", e.title)
        assertEquals(dubai(2026, 11, 13, 9, 30), e.startAt)
        assertEquals(10, e.reminderMinutes)
        handler.undo(id)
        assertTrue(db.eventDao().get(id)!!.sync.deleted)
    }

    @Test fun noDefaultReminderWhenTheSettingIsNone() = runBlocking {
        settings.set(CalendarKeys.DefaultReminderMinutes, -1)
        val id = handler.execute(input("gym tomorrow 6pm"), handler.defaultAction(input("x")))
        assertNull(db.eventDao().get(id)!!.reminderMinutes)
    }
}
