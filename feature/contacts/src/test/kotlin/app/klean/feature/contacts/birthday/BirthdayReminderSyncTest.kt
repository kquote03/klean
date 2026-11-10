package app.klean.feature.contacts.birthday

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.reminder.DefaultReminderRepository
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.DAY
import app.klean.feature.contacts.DubaiZone
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.FakeModuleGate
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.tempDevicePrefs
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

object FakeBirthdayText : BirthdayText {
    override fun today(name: String, age: Int?) = if (age != null) "$name turns $age today" else "It's $name's birthday today"
    override fun inDays(name: String, days: Int, age: Int?) = "$name's birthday is in $days days"
}

@RunWith(AndroidJUnit4::class)
class BirthdayReminderSyncTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = FakeModuleGate()
    private lateinit var sync: BirthdayReminderSync
    private val reminders = DefaultReminderRepository(db, stamper, clock, setOf(app.klean.core.data.reminder.ReminderChangeListener { id -> sync.onChanged(id) }))
    private val contacts = DefaultContactRepository(db, stamper, clock, settings, setOf(app.klean.feature.contacts.data.ContactChangeListener { ids -> sync.onContactsChanged(ids) }))

    init {
        sync = BirthdayReminderSync({ reminders }, { contacts }, db, { settings }, clock, DubaiZone, gate, FakeBirthdayText)
    }

    @After fun close() = db.close()

    private suspend fun reminder(contactId: String, n: Int) = db.reminderDao().get(BirthdayReminderSync.reminderId(contactId, n))
    private suspend fun live(contactId: String, n: Int) = reminder(contactId, n)?.takeUnless { it.sync.deleted }

    @Test fun onTheDayAndThreeDaysBeforeAtNineByDefault() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1)))
        val onDay = live(id, 0)!!
        assertEquals(dubai(2026, 12, 1, 9), onDay.fireAt)
        assertEquals("It's Sara's birthday today", onDay.title)
        assertFalse(onDay.isAlarm)
        val before = live(id, 3)!!
        assertEquals(dubai(2026, 11, 28, 9), before.fireAt)
        assertEquals("Sara's birthday is in 3 days", before.title)
        assertEquals(null, live(id, 1))
    }

    @Test fun ageWhenTheYearIsKnownAndShowAgeIsOn() = runBlocking {
        val id = contacts.add(ContactDraft("Omar", birthday = Birthday(12, 1, 1990), reminderDays = listOf(0)))
        assertEquals("Omar turns 36 today", live(id, 0)!!.title)
        settings.set(ContactsKeys.ShowAge, false)
        sync.reschedule()
        assertEquals("It's Omar's birthday today", live(id, 0)!!.title)
    }

    @Test fun feb29GoesOffOnFeb28InACommonYear() = runBlocking {
        val id = contacts.add(ContactDraft("Lina", birthday = Birthday(2, 29), reminderDays = listOf(0, 3)))
        assertEquals(dubai(2027, 2, 28, 9), live(id, 0)!!.fireAt)
        assertEquals(dubai(2027, 2, 25, 9), live(id, 3)!!.fireAt)
    }

    @Test fun aBirthdayLaterTodayStillRemindsTodayAndMovesOnOnceHandled() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(11, 10), reminderDays = listOf(0, 3)))
        // 09:00 has passed: still today's (the Reminders module rings it at once).
        assertEquals(dubai(2026, 11, 10, 9), live(id, 0)!!.fireAt)
        // The 3-days-before day is gone: next year's.
        assertEquals(dubai(2027, 11, 7, 9), live(id, 3)!!.fireAt)
        reminders.dismiss(BirthdayReminderSync.reminderId(id, 0))
        assertEquals(dubai(2027, 11, 10, 9), live(id, 0)!!.fireAt)
        assertEquals(DefaultReminderRepository.SCHEDULED, live(id, 0)!!.state)
        // A resync on the same day keeps it next year.
        sync.reschedule()
        assertEquals(dubai(2027, 11, 10, 9), live(id, 0)!!.fireAt)
    }

    @Test fun changingDaysRemovesTheOldOnesAndOffRemovesAll() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1)))
        contacts.setReminderDays(id, listOf(7))
        assertEquals(null, live(id, 0))
        assertEquals(null, live(id, 3))
        assertEquals(dubai(2026, 11, 24, 9), live(id, 7)!!.fireAt)
        contacts.setReminderDays(id, emptyList())
        assertEquals(null, live(id, 7))
    }

    @Test fun deletingTheContactOrTheBirthdayRemovesItsReminders() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1)))
        contacts.update(id, ContactDraft("Sara", birthday = null))
        assertEquals(null, live(id, 0))
        contacts.update(id, ContactDraft("Sara", birthday = Birthday(12, 1)))
        assertTrue(live(id, 0) != null)
        contacts.delete(id)
        assertEquals(null, live(id, 0))
        assertEquals(null, live(id, 3))
    }

    @Test fun birthdaysModuleOffRemovesAndOnRestores() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1)))
        gate.setEnabled(ModuleId.BIRTHDAYS, false)
        sync.reschedule()
        assertEquals(null, live(id, 0))
        gate.setEnabled(ModuleId.BIRTHDAYS, true)
        sync.reschedule()
        assertEquals(dubai(2026, 12, 1, 9), live(id, 0)!!.fireAt)
    }

    @Test fun rescheduleWritesNothingWhenNothingChanged() = runBlocking {
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1)))
        val before = reminder(id, 0)!!.sync.changeSeq
        clock.nowMs += DAY
        sync.reschedule()
        assertEquals(before, reminder(id, 0)!!.sync.changeSeq)
    }

    @Test fun theReminderHourIsASetting() = runBlocking {
        settings.set(ContactsKeys.ReminderHour, 8)
        val id = contacts.add(ContactDraft("Sara", birthday = Birthday(12, 1), reminderDays = listOf(0)))
        assertEquals(dubai(2026, 12, 1, 8), live(id, 0)!!.fireAt)
    }
}
