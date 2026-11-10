package app.klean.feature.contacts.merge

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.FakeDeviceContacts
import app.klean.feature.contacts.contact
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.device.DeviceContact
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.mirror.ProviderJoin
import app.klean.feature.contacts.tempDevicePrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicatesTest {
    private val phone = """[{"label":"mobile","value":"+971 50 123 4567"}]"""

    @Test fun sameNameAndSharedPhoneIsAPair() {
        val a = contact("a", "Sara Haddad", phones = phone)
        val b = contact("b", "sara  haddad", phones = """[{"label":"","value":"0501234567"}]""")
        val pairs = DuplicateFinder.find(listOf(a, b), emptyList())
        assertEquals(1, pairs.size)
        assertEquals("klean:a|klean:b", pairs.single().key)
    }

    @Test fun sameNameAloneOrSharedPhoneAloneIsNot() {
        val a = contact("a", "Sara", phones = phone)
        assertTrue(DuplicateFinder.find(listOf(a, contact("b", "Sara")), emptyList()).isEmpty())
        assertTrue(DuplicateFinder.find(listOf(a, contact("b", "Omar", phones = phone)), emptyList()).isEmpty())
    }

    @Test fun sharedEmailAcrossAccountsKeepsTheKleanOne() {
        val a = contact("a", "Omar", emails = """[{"label":"","value":"Omar@Example.com"}]""")
        val d = DeviceContact(7, "me@dav", "Omar", emails = listOf(LabeledValue("", "omar@example.com")))
        val pair = DuplicateFinder.find(listOf(a), listOf(d)).single()
        assertEquals("a", pair.a.kleanId)
        assertEquals(7L, pair.b.rawId)
    }

    @Test fun twoOtherAccountRecordsAreLeftAloneAndKeptApartPairsAreSkipped() {
        val d1 = DeviceContact(1, "x", "Omar", phones = listOf(LabeledValue("", "0501234567")))
        val d2 = DeviceContact(2, "y", "Omar", phones = listOf(LabeledValue("", "0501234567")))
        assertTrue(DuplicateFinder.find(emptyList(), listOf(d1, d2)).isEmpty())
        val a = contact("a", "Sara", phones = phone)
        val b = contact("b", "Sara", phones = phone)
        assertTrue(DuplicateFinder.find(listOf(a, b), emptyList(), setOf("klean:a|klean:b")).isEmpty())
    }

    @Test fun shortNumbersDoNotMatch() {
        val a = contact("a", "Sara", phones = """[{"label":"","value":"123"}]""")
        val b = contact("b", "Sara", phones = """[{"label":"","value":"123"}]""")
        assertTrue(DuplicateFinder.find(listOf(a, b), emptyList()).isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class MergeOfferTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = DefaultContactRepository(db, stamper, clock, settings, emptySet())
    private val device = FakeDeviceContacts()
    private val joined = mutableListOf<Pair<String, Long>>()
    private val offer = MergeOffer(repo, device, settings, object : ProviderJoin {
        override suspend fun keepTogether(id: String, otherRawId: Long) { joined += id to otherRawId }
    })

    @After fun close() = db.close()

    @Test fun mergingTheLastPairEndsTheOfferForGood() = runBlocking {
        val a = repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "0501234567"))))
        clock.nowMs += 1000
        val b = repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "+971501234567")), birthday = Birthday(4, 12)))
        val pair = offer.pairs().single()
        assertEquals(a, pair.a.kleanId)
        offer.merge(pair)
        assertEquals(listOf(a), repo.live().map { it.id })
        assertEquals(Birthday(4, 12), repo.get(a)!!.birthday)
        assertTrue(settings.get(ContactsKeys.MergeOfferDone))
        // A new duplicate later is not offered again (one-time).
        repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "0501234567"))))
        assertTrue(offer.pairs().isEmpty())
        assertTrue(db.contactDao().get(b)!!.sync.deleted)
    }

    @Test fun mergingWithAnotherAccountFoldsItInAndJoinsTheCards() = runBlocking {
        val a = repo.add(ContactDraft("Omar", emails = listOf(LabeledValue("", "omar@example.com"))))
        device.list += DeviceContact(9, "me@dav", "Omar", phones = listOf(LabeledValue("mobile", "0509999999")),
            emails = listOf(LabeledValue("", "omar@example.com")), birthday = Birthday(1, 2, 1990))
        offer.merge(offer.pairs().single())
        val kept = repo.get(a)!!
        assertEquals(Birthday(1, 2, 1990), kept.birthday)
        assertEquals(listOf(a to 9L), joined)
    }

    @Test fun keepingApartAndNotNow() = runBlocking {
        repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "0501234567"))))
        repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "0501234567"))))
        repo.add(ContactDraft("Lina", phones = listOf(LabeledValue("", "0507777777"))))
        repo.add(ContactDraft("Lina", phones = listOf(LabeledValue("", "0507777777"))))
        val pairs = offer.pairs()
        assertEquals(2, pairs.size)
        offer.keepApart(pairs.first())
        assertEquals(1, offer.pairs().size)
        offer.setAside()
        assertTrue(offer.pairs().isEmpty())
        assertEquals(4, repo.live().size)
    }
}
