package app.klean.feature.contacts.mirror

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.ContactEntity
import app.klean.feature.contacts.FakeModuleGate
import app.klean.feature.contacts.contact
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class FakeContactsProvider(var granted: Boolean = true) : ContactsProviderOps {
    var nextId = 100L
    /** rawId → (SOURCE_ID, rows) */
    val raws = linkedMapOf<Long, Pair<String, List<DataRow>>>()
    val together = mutableListOf<Pair<Long, Long>>()
    override fun canWrite() = granted
    override fun sourceIds(): Map<String, Long> = raws.entries.associate { it.value.first to it.key }
    override fun insert(raw: Map<String, Any?>, rows: List<DataRow>): Long {
        val id = nextId++
        raws[id] = (raw[RawContacts.SOURCE_ID] as String) to rows
        return id
    }
    override fun replace(rawId: Long, rows: List<DataRow>) {
        raws[rawId] = raws.getValue(rawId).first to rows
    }
    override fun delete(rawId: Long) {
        raws.remove(rawId)
    }
    override fun keepTogether(rawA: Long, rawB: Long) {
        together += rawA to rawB
    }
}

@RunWith(AndroidJUnit4::class)
class KleanContactsMirrorTest {
    private val provider = FakeContactsProvider()
    private val gate = FakeModuleGate()
    private val store = linkedMapOf<String, ContactEntity>()
    private var ensured = 0
    private val mirror = KleanContactsMirror(
        provider, MirrorSource({ store[it] }, { store.values.toList() }), gate, "Klean", "app.klean.test", { ensured++ },
    )

    private fun rows(sourceId: String) = provider.raws.values.first { it.first == sourceId }.second

    @Test fun rowsCarryNamePhonesEmailsAndBirthday() {
        val c = contact(
            "c1", "Sara Haddad", month = 2, day = 29,
            phones = """[{"label":"mobile","value":"+971501234567"},{"label":"gym","value":"123"}]""",
            emails = """[{"label":"work","value":"sara@example.com"}]""",
        )
        val rows = ContactRows.data(c)
        assertEquals("Sara Haddad", rows.first { it.mimeType == StructuredName.CONTENT_ITEM_TYPE }.values[StructuredName.DISPLAY_NAME])
        val phones = rows.filter { it.mimeType == Phone.CONTENT_ITEM_TYPE }
        assertEquals(Phone.TYPE_MOBILE, phones[0].values[Phone.TYPE])
        assertNull(phones[0].values[Phone.LABEL])
        assertEquals(Phone.TYPE_CUSTOM, phones[1].values[Phone.TYPE])
        assertEquals("gym", phones[1].values[Phone.LABEL])
        assertEquals(Email.TYPE_WORK, rows.first { it.mimeType == Email.CONTENT_ITEM_TYPE }.values[Email.TYPE])
        val event = rows.first { it.mimeType == Event.CONTENT_ITEM_TYPE }.values
        assertEquals("--02-29", event[Event.START_DATE])
        assertEquals(Event.TYPE_BIRTHDAY, event[Event.TYPE])
        assertEquals("2000-02-29", ContactRows.data(contact("c2", "Y", 2, 29, 2000)).first { it.mimeType == Event.CONTENT_ITEM_TYPE }.values[Event.START_DATE])
        val raw = ContactRows.rawContact(c, "Klean", "app.klean.test")
        assertEquals("c1", raw[RawContacts.SOURCE_ID])
        assertEquals("app.klean.test", raw[RawContacts.ACCOUNT_TYPE])
    }

    @Test fun writesFollowInsertUpdateDelete() = runBlocking {
        store["c1"] = contact("c1", "Sara")
        mirror.onContactsChanged(listOf("c1"))
        assertEquals(1, provider.raws.size)
        assertEquals(1, ensured)
        store["c1"] = contact("c1", "Sara H")
        mirror.onContactsChanged(listOf("c1"))
        assertEquals(1, provider.raws.size)
        assertEquals("Sara H", rows("c1").first().values[StructuredName.DISPLAY_NAME])
        store["c1"] = contact("c1", "Sara H", deleted = true)
        mirror.onContactsChanged(listOf("c1"))
        assertTrue(provider.raws.isEmpty())
    }

    @Test fun nothingWithoutPermission() = runBlocking {
        provider.granted = false
        store["c1"] = contact("c1", "Sara")
        mirror.onContactsChanged(listOf("c1"))
        mirror.reschedule()
        assertTrue(provider.raws.isEmpty())
    }

    @Test fun resyncAddsMissingAndRemovesOrphans() = runBlocking {
        provider.raws[5] = "gone" to emptyList()
        store["c1"] = contact("c1", "Sara")
        store["c2"] = contact("c2", "Omar")
        mirror.reschedule()
        assertEquals(setOf("c1", "c2"), provider.sourceIds().keys)
    }

    @Test fun moduleOffRemovesEverythingAndKeepsItOff() = runBlocking {
        store["c1"] = contact("c1", "Sara")
        mirror.reschedule()
        gate.setEnabled(ModuleId.CONTACTS, false)
        mirror.reschedule()
        assertTrue(provider.raws.isEmpty())
        mirror.onContactsChanged(listOf("c1"))
        assertTrue(provider.raws.isEmpty())
        gate.setEnabled(ModuleId.CONTACTS, true)
        mirror.reschedule()
        assertEquals(setOf("c1"), provider.sourceIds().keys)
    }

    @Test fun contactsFromAnotherAccountAreNotMirrored() = runBlocking {
        store["c1"] = contact("c1", "Sara").copy(sourceAccount = "bitfire.at.davdroid:me")
        mirror.reschedule()
        assertTrue(provider.raws.isEmpty())
    }
}
