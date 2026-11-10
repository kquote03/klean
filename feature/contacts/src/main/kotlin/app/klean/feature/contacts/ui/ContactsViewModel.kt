package app.klean.feature.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.birthday.Birthdays
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.emailList
import app.klean.feature.contacts.data.phoneList
import app.klean.feature.contacts.device.DeviceContact
import app.klean.feature.contacts.device.DeviceContacts
import app.klean.feature.contacts.merge.DuplicatePair
import app.klean.feature.contacts.merge.MergeOffer
import app.klean.feature.contacts.today.BirthdayDays
import app.klean.shared.module.ModuleId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class ContactsTab { CONTACTS, BIRTHDAYS }

/** One row of the A–Z list. [rawId] is set for a contact in another account (DAVx5). */
data class ContactRow(val key: String, val name: String, val detail: String?, val contactId: String? = null, val rawId: Long? = null)

data class ContactSection(val header: String, val rows: List<ContactRow>)

/** One row of the Birthdays tab. */
data class BirthdayRow(val contactId: String, val name: String, val date: LocalDate, val daysUntil: Int, val age: Int?)

/** "Coming up" (next 60 days), then the rest by month (1–12) starting after the coming-up window. */
data class BirthdaySections(val comingUp: List<BirthdayRow> = emptyList(), val byMonth: List<Pair<Int, List<BirthdayRow>>> = emptyList()) {
    val isEmpty get() = comingUp.isEmpty() && byMonth.isEmpty()
}

data class ContactsUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val sections: List<ContactSection> = emptyList(),
    /** DAVx5 contacts by account name. */
    val other: List<ContactSection> = emptyList(),
    val canReadPhone: Boolean = false,
    val mergePairs: List<DuplicatePair> = emptyList(),
    val birthdaysOn: Boolean = true,
    val birthdays: BirthdaySections = BirthdaySections(),
    val today: LocalDate? = null,
)

/** Pure list building, so it is tested without a screen. */
object ContactLists {
    const val COMING_UP_DAYS = 60

    fun matches(name: String, detail: List<String>, q: String): Boolean {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return true
        val digits = t.filter { it.isDigit() }
        return name.lowercase().contains(t) || detail.any { it.lowercase().contains(t) } ||
            (digits.length >= 3 && detail.any { d -> d.filter { it.isDigit() }.contains(digits) })
    }

    fun sections(contacts: List<ContactEntity>, query: String): List<ContactSection> = contacts
        .filter { !it.sync.deleted && it.sourceAccount == DefaultContactRepository.KLEAN }
        .filter { c -> matches(c.displayName, c.phoneList.map { it.value } + c.emailList.map { it.value }, query) }
        .map { c -> ContactRow("klean:${c.id}", c.displayName, c.phoneList.firstOrNull()?.value ?: c.emailList.firstOrNull()?.value, contactId = c.id) }
        .sortedBy { it.name.lowercase() }
        .groupBy { initial(it.name) }
        .toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
        .map { (k, v) -> ContactSection(k, v) }

    fun other(device: List<DeviceContact>, query: String): List<ContactSection> = device
        .filter { d -> matches(d.displayName, d.phones.map { it.value } + d.emails.map { it.value }, query) }
        .groupBy { it.accountName }
        .toSortedMap()
        .map { (account, l) ->
            ContactSection(account, l.sortedBy { it.displayName.lowercase() }.map {
                ContactRow("raw:${it.rawId}", it.displayName, it.phones.firstOrNull()?.value ?: it.emails.firstOrNull()?.value, rawId = it.rawId)
            })
        }

    fun initial(name: String): String = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

    fun birthdays(contacts: List<ContactEntity>, today: LocalDate, showAge: Boolean): BirthdaySections {
        val rows = contacts.filter { !it.sync.deleted }.mapNotNull { c ->
            val b = c.birthday ?: return@mapNotNull null
            val next = Birthdays.next(b, today)
            BirthdayRow(c.id, c.displayName, next, Birthdays.daysUntil(b, today), if (showAge) Birthdays.ageOn(b, next) else null)
        }.sortedWith(compareBy({ it.daysUntil }, { it.name.lowercase() }))
        val (soon, later) = rows.partition { it.daysUntil <= COMING_UP_DAYS }
        // The rest in calendar order, starting from the month after the window.
        val byMonth = later.groupBy { it.date.month.ordinal + 1 }.entries
            .sortedBy { e -> e.value.minOf { it.daysUntil } }
            .map { it.key to it.value.sortedWith(compareBy({ r -> r.date.day }, { r -> r.name.lowercase() })) }
        return BirthdaySections(soon, byMonth)
    }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val device: DeviceContacts,
    private val offer: MergeOffer,
    private val settings: SettingsRepository,
    private val access: ContactsAccessHook,
    gate: ModuleGate,
    days: BirthdayDays,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val others = MutableStateFlow<List<DeviceContact>>(emptyList())
    private val canRead = MutableStateFlow(device.canRead())
    private val pairs = MutableStateFlow<List<DuplicatePair>>(emptyList())

    private data class Extra(val query: String, val others: List<DeviceContact>, val canRead: Boolean, val pairs: List<DuplicatePair>)

    val state: StateFlow<ContactsUiState> = combine(
        repo.observeAll(),
        combine(query, others, canRead, pairs, ::Extra),
        gate.disabledIds.map { ModuleId.BIRTHDAYS !in it },
        settings.observe(ContactsKeys.ShowAge),
        days.days(),
    ) { contacts, x, birthdaysOn, showAge, today ->
        ContactsUiState(
            loaded = true, query = x.query,
            sections = ContactLists.sections(contacts, x.query),
            other = ContactLists.other(x.others, x.query),
            canReadPhone = x.canRead, mergePairs = x.pairs, birthdaysOn = birthdaysOn,
            birthdays = ContactLists.birthdays(contacts, today, showAge), today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContactsUiState())

    init {
        refresh()
    }

    fun setQuery(q: String) {
        query.value = q
    }

    /** Reads the phone's other-account contacts and the merge offer again (start, permission answered). */
    fun refresh() {
        canRead.value = device.canRead()
        viewModelScope.launch {
            others.value = safe { device.contacts() } ?: emptyList()
            pairs.value = safe { offer.pairs() } ?: emptyList()
        }
    }

    /** The contacts permission was answered: mirror Klean's contacts if now allowed, then read again. */
    fun onAccessResult() {
        viewModelScope.launch { safe { access.onAccessChanged() } }
        refresh()
    }

    fun merge(pair: DuplicatePair) = act { offer.merge(pair) }
    fun keepApart(pair: DuplicatePair) = act { offer.keepApart(pair) }
    fun notNow() = act { offer.setAside() }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            safe { block() }
            pairs.value = safe { offer.pairs() } ?: emptyList()
            others.value = safe { device.contacts() } ?: others.value
        }
    }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("Klean", "contacts screen", e)
        null
    }
}
