package app.klean.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.DubaiZone
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.FakeDeviceContacts
import app.klean.feature.contacts.FakeModuleGate
import app.klean.feature.contacts.contact
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.phoneList
import app.klean.feature.contacts.data.reminderDays
import app.klean.feature.contacts.date
import app.klean.feature.contacts.device.DeviceContact
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.merge.MergeOffer
import app.klean.feature.contacts.mirror.ProviderJoin
import app.klean.feature.contacts.tempDevicePrefs
import app.klean.feature.contacts.today.BirthdayDays
import app.klean.shared.module.ModuleId
import app.klean.shared.vcard.VCardVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.runner.RunWith

class ContactListsTest {
    @Test fun sectionsAreAToZWithOthersLastAndSearchMatchesNumbers() {
        val list = listOf(
            contact("1", "omar"), contact("2", "Sara", phones = """[{"label":"","value":"+971 50 123 4567"}]"""),
            contact("3", "123 Taxi"), contact("4", "Amal"), contact("5", "Gone", deleted = true),
        )
        val s = ContactLists.sections(list, "")
        assertEquals(listOf("A", "O", "S", "#"), s.map { it.header })
        assertEquals("+971 50 123 4567", s[2].rows.single().detail)
        assertEquals(listOf("Sara"), ContactLists.sections(list, "1234").flatMap { it.rows }.map { it.name })
        assertEquals(listOf("omar"), ContactLists.sections(list, "OM").flatMap { it.rows }.map { it.name })
    }

    @Test fun birthdaysComingUpThenByMonth() {
        val today = date(2026, 11, 10)
        val b = ContactLists.birthdays(
            listOf(contact("a", "Sara", 11, 12), contact("b", "Omar", 3, 3), contact("c", "Lina", 2, 29), contact("d", "Amal", 3, 1), contact("e", "None")),
            today, showAge = false,
        )
        assertEquals(listOf("Sara"), b.comingUp.map { it.name })
        assertEquals(listOf(2, 3), b.byMonth.map { it.first })
        assertEquals(date(2027, 2, 28), b.byMonth[0].second.single().date)
        assertEquals(listOf("Amal", "Omar"), b.byMonth[1].second.map { it.name })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ContactsViewModelsTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = DefaultContactRepository(db, stamper, clock, settings, emptySet())
    private val device = FakeDeviceContacts()
    private val gate = FakeModuleGate()
    private val days = object : BirthdayDays(clock, DubaiZone) { override fun days() = flowOf(date(2026, 11, 10)) }
    private val offer = MergeOffer(repo, device, settings, object : ProviderJoin { override suspend fun keepTogether(id: String, otherRawId: Long) = Unit })
    private val store = ViewModelStore()
    private var accessChanges = 0

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        db.close()
    }

    private fun <T : androidx.lifecycle.ViewModel> keep(vm: T): T = vm.also { store.put("vm${store.keys().size}", it) }
    private fun list() = keep(ContactsViewModel(repo, device, offer, settings, { accessChanges++ }, gate, days))
    private fun edit(state: Map<String, Any?> = emptyMap()) = keep(ContactEditViewModel(SavedStateHandle(state), repo, device, settings))

    private fun <T> await(read: () -> T, until: (T) -> Boolean): T {
        repeat(200) {
            val v = read()
            if (until(v)) return v
            Thread.sleep(10)
        }
        return read()
    }

    @Test fun listShowsKleanAndDavContactsMergePairsAndHidesBirthdaysTabWhenOff() = runBlocking {
        repo.add(ContactDraft("Sara", phones = listOf(LabeledValue("", "0501234567")), birthday = Birthday(11, 12)))
        device.list += DeviceContact(3, "me@dav", "Sara", phones = listOf(LabeledValue("", "+971501234567")))
        val vm = list()
        val s = await({ vm.state.value }) { it.loaded && it.sections.isNotEmpty() && it.mergePairs.isNotEmpty() }
        assertEquals(listOf("Sara"), s.sections.flatMap { it.rows }.map { it.name })
        assertEquals("me@dav", s.other.single().header)
        assertEquals(1, s.mergePairs.size)
        assertEquals(listOf("Sara"), s.birthdays.comingUp.map { it.name })
        assertTrue(s.birthdaysOn)
        gate.setEnabled(ModuleId.BIRTHDAYS, false)
        assertFalse(await({ vm.state.value }) { !it.birthdaysOn }.birthdaysOn)
        vm.notNow()
        assertTrue(await({ vm.state.value }) { it.mergePairs.isEmpty() }.mergePairs.isEmpty())
        vm.onAccessResult()
        assertEquals(1, await({ accessChanges }) { it > 0 })
    }

    @Test fun newContactTakesDefaultsAndSaves() = runBlocking {
        settings.set(ContactsKeys.DefaultReminders, "0")
        val vm = edit()
        await({ vm.state.value }) { it.loaded }
        assertEquals(setOf(0), vm.state.value.form.reminderDays)
        vm.save()
        assertEquals(FormProblem.NAME, vm.state.value.problem)
        vm.edit { it.copy(name = "Sara") }
        vm.setPhone(0, LabeledValue("mobile", "0501234567"))
        vm.setBirthdayMonth(2)
        vm.setBirthdayDay(29)
        vm.setBirthdayYear("2001")
        vm.save()
        assertEquals(FormProblem.BIRTHDAY, vm.state.value.problem)
        vm.setBirthdayYear("")
        vm.toggleReminder(3)
        vm.save()
        await({ vm.state.value }) { it.done }
        val c = repo.live().single()
        assertEquals("Sara", c.displayName)
        assertEquals(Birthday(2, 29), c.birthday)
        assertEquals(listOf(0, 3), c.reminderDays)
        assertEquals("0501234567", c.phoneList.single().value)
    }

    @Test fun editingLoadsTheContactAndCanTurnRemindersOffOrDelete() = runBlocking {
        val id = repo.add(ContactDraft("Omar", birthday = Birthday(3, 3, 1990)))
        val vm = edit(mapOf("contactId" to id))
        val s = await({ vm.state.value }) { it.loaded }
        assertFalse(s.isNew)
        assertEquals(3, s.form.month)
        assertEquals("1990", s.form.year)
        vm.toggleReminder(0)
        vm.toggleReminder(3)
        vm.setBirthdayMonth(null)
        vm.save()
        await({ vm.state.value }) { it.done }
        val c = repo.get(id)!!
        assertNull(c.birthday)
        assertEquals(emptyList<Int>(), c.reminderDays)
        val vm2 = edit(mapOf("contactId" to id))
        await({ vm2.state.value }) { it.loaded }
        vm2.delete()
        await({ vm2.state.value }) { it.done }
        assertTrue(repo.live().isEmpty())
    }

    @Test fun aDavContactIsSavedThroughTheProvider() = runBlocking {
        device.list += DeviceContact(7, "me@dav", "Lina", phones = listOf(LabeledValue("", "050")))
        val vm = edit(mapOf("rawId" to 7L))
        val s = await({ vm.state.value }) { it.loaded }
        assertTrue(s.isOther)
        assertEquals("Lina", s.form.name)
        vm.edit { it.copy(name = "Lina N") }
        vm.save()
        await({ vm.state.value }) { it.done }
        assertEquals("Lina N", device.updates.single().second.displayName)
        assertTrue(repo.live().isEmpty())
    }

    @Test fun settingsImportAndExport() = runBlocking {
        val files = object : VcfFiles {
            var written: String? = null
            override suspend fun read(uri: String) = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Sara\r\nEND:VCARD\r\n"
            override suspend fun write(uri: String, text: String) { written = text }
        }
        val vm = keep(ContactsSettingsViewModel(repo, device, files, { accessChanges++ }))
        vm.import("content://x")
        assertEquals(ContactsMessage.Imported(1, 0), await({ vm.state.value.message }) { it != null })
        vm.messageShown()
        vm.export("content://y", VCardVersion.V4)
        assertEquals(ContactsMessage.Exported(1), await({ vm.state.value.message }) { it != null })
        assertTrue(files.written!!.contains("VERSION:4.0"))
        assertEquals(listOf("me@dav"), vm.state.value.davAccounts)
    }
}
