package app.klean.feature.contacts.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.HOUR
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.tempDevicePrefs
import app.klean.shared.vcard.VCardCodec
import app.klean.shared.vcard.VCardVersion
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {
    private val now = dubai(2026, 11, 10, 12)
    private val clock = FakeClock(now)
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val changed = mutableListOf<List<String>>()
    private val repo = DefaultContactRepository(
        db, stamper, clock, settings, setOf(ContactChangeListener { synchronized(changed) { changed += it } }),
    )

    @After fun close() = db.close()

    @Test fun addTrimsStoresJsonAndDefaultRemindersAndTellsListeners() = runBlocking {
        val id = repo.add(
            ContactDraft(
                displayName = "  Sara   Haddad ",
                phones = listOf(LabeledValue("mobile", " +971 50 123 4567 "), LabeledValue("home", "  ")),
                emails = listOf(LabeledValue("", "sara@example.com")),
                birthday = Birthday(2, 29),
            ),
        )
        val c = db.contactDao().get(id)!!
        assertEquals("Sara Haddad", c.displayName)
        assertEquals(listOf(LabeledValue("mobile", "+971 50 123 4567")), c.phoneList)
        assertEquals(listOf(LabeledValue("", "sara@example.com")), c.emailList)
        assertEquals(Birthday(2, 29), c.birthday)
        assertEquals(listOf(0, 3), c.reminderDays)
        assertEquals("KLEAN", c.sourceAccount)
        assertEquals(now, c.sync.createdAt)
        assertTrue(c.sync.changeSeq > 0)
        assertEquals(listOf(listOf(id)), changed)
    }

    @Test fun newContactsTakeTheDefaultRemindersFromSettings() = runBlocking {
        settings.set(ContactsKeys.DefaultReminders, "")
        val id = repo.add(ContactDraft("Omar", birthday = Birthday(3, 3)))
        assertEquals(emptyList<Int>(), db.contactDao().get(id)!!.reminderDays)
        val chosen = repo.add(ContactDraft("Lina", reminderDays = listOf(7, 1, 7)))
        assertEquals(listOf(1, 7), db.contactDao().get(chosen)!!.reminderDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRefused(): Unit = runBlocking { repo.add(ContactDraft("   ")); Unit }

    @Test fun updateKeepsCreatedAtAndCarriedFieldsAndAdvancesChangeSeq() = runBlocking {
        val id = repo.add(ContactDraft("Sara", anniversary = "--06-01", custom = """{"vcard":["NICKNAME:Sars"]}"""))
        val before = db.contactDao().get(id)!!
        clock.nowMs += HOUR
        repo.update(id, ContactDraft("Sara H", org = "Klean", reminderDays = listOf(1)))
        val after = db.contactDao().get(id)!!
        assertEquals("Sara H", after.displayName)
        assertEquals("Klean", after.org)
        assertEquals("--06-01", after.anniversary)
        assertEquals("""{"vcard":["NICKNAME:Sars"]}""", after.custom)
        assertEquals(listOf(1), after.reminderDays)
        assertEquals(before.sync.createdAt, after.sync.createdAt)
        assertEquals(now + HOUR, after.sync.updatedAt)
        assertTrue(after.sync.changeSeq > before.sync.changeSeq)
    }

    @Test fun deleteTombstones() = runBlocking {
        val id = repo.add(ContactDraft("Sara"))
        repo.delete(id)
        assertTrue(db.contactDao().get(id)!!.sync.deleted)
        assertTrue(repo.live().isEmpty())
        assertNull(repo.findByName("sara"))
    }

    @Test fun setBirthdayFindsByNameIgnoringCaseOrCreates() = runBlocking {
        val id = repo.add(ContactDraft("Sara Haddad"))
        assertEquals(id, repo.setBirthday("sara  haddad", Birthday(3, 3)))
        assertEquals(Birthday(3, 3), db.contactDao().get(id)!!.birthday)
        val other = repo.setBirthday("Omar", Birthday(12, 1, 1990))
        assertEquals("Omar", db.contactDao().get(other)!!.displayName)
        assertEquals(Birthday(12, 1, 1990), db.contactDao().get(other)!!.birthday)
        assertEquals(2, repo.live().size)
    }

    @Test fun birthdayValidationAllowsFeb29WithoutAYearOrInALeapYear() {
        assertTrue(Birthday.isValid(2, 29, null))
        assertTrue(Birthday.isValid(2, 29, 2000))
        assertTrue(!Birthday.isValid(2, 29, 1999))
        assertTrue(!Birthday.isValid(4, 31, null))
        assertTrue(!Birthday.isValid(13, 1, null))
        assertNull(Birthday.of(2, 30, null))
    }

    @Test fun vcfImportAddsThenUpdatesByUidAndExportRoundTrips() = runBlocking {
        val text = javaClass.getResource("/vcf/three-cards.vcf")!!.readText()
        assertEquals(ImportResult(added = 3, updated = 0), repo.importVcf(text))
        assertEquals(ImportResult(added = 0, updated = 3), repo.importVcf(text))
        val all = repo.live().associateBy { it.displayName }
        assertEquals(setOf("Sara Haddad", "Omar Khalil", "Lina Nasser"), all.keys)
        val sara = all.getValue("Sara Haddad")
        assertEquals(Birthday(4, 12, 1985), sara.birthday)
        assertEquals("mobile", sara.phoneList.first().label)
        assertEquals("Dubai", sara.addressList.single().locality)
        val omar = all.getValue("Omar Khalil")
        assertEquals(Birthday(2, 29), omar.birthday)
        assertEquals("work", omar.emailList.single().label)
        // NICKNAME is not modelled: it is kept and written back.
        val out = repo.exportVcf(VCardVersion.V3)
        val cards = VCardCodec.parse(out).associateBy { it.formattedName }
        assertEquals(3, cards.size)
        assertTrue(cards.getValue("Lina Nasser").extras.any { it.name == "NICKNAME" && it.value == "Lini" })
        assertEquals(sara.remoteUid, cards.getValue("Sara Haddad").uid)
        assertTrue(out.contains("VERSION:3.0"))
        assertTrue(out.contains("BDAY:--02-29"))
        assertEquals(3, repo.exportCount())
        // Re-importing the export changes nothing structural.
        assertEquals(ImportResult(0, 3), repo.importVcf(out))
        assertEquals(3, repo.live().size)
    }

    @Test fun vcfV4ExportUsesNoYearForm() = runBlocking {
        repo.add(ContactDraft("Omar", birthday = Birthday(2, 29)))
        val out = repo.exportVcf(VCardVersion.V4)
        assertTrue(out, out.contains("BDAY:--0229"))
        assertTrue(out.contains("VERSION:4.0"))
    }

    @Test fun importOfSomethingElseFails() {
        val failed = runCatching { runBlocking { repo.importVcf("hello there") } }.isFailure
        assertTrue(failed)
    }

    @Test fun mergeFoldsAndTombstonesTheOther() = runBlocking {
        val a = repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("mobile", "+971501234567")), birthday = Birthday(4, 12)))
        val b = repo.add(
            ContactDraft(
                "sara", phones = listOf(LabeledValue("", "050 123 4567")),
                emails = listOf(LabeledValue("", "sara@example.com")), birthday = Birthday(4, 12, 1985), notes = "Met at work",
            ),
        )
        repo.merge(a, b)
        val kept = db.contactDao().get(a)!!
        assertEquals(1, kept.phoneList.size)
        assertEquals("sara@example.com", kept.emailList.single().value)
        assertEquals(Birthday(4, 12, 1985), kept.birthday)
        assertEquals("Met at work", kept.notes)
        assertTrue(db.contactDao().get(b)!!.sync.deleted)
        assertEquals(listOf(a), repo.live().map { it.id })
    }
}
