package app.klean.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.birthday.Birthdays
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.ContactVcf
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.PostalAddress
import app.klean.feature.contacts.data.addressList
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.emailList
import app.klean.feature.contacts.data.phoneList
import app.klean.feature.contacts.data.reminderDays
import app.klean.feature.contacts.device.DeviceContacts
import app.klean.feature.contacts.today.BirthdayDays
import app.klean.shared.vcard.ContentLines
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class ContactDetail(
    val name: String,
    val org: String? = null,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val birthday: Birthday? = null,
    val nextBirthday: LocalDate? = null,
    val turns: Int? = null,
    val reminderDays: List<Int> = emptyList(),
    val anniversary: String? = null,
    val notes: String? = null,
    /** Other vCard properties as "NAME: value" (nickname, website, …), shown as they are. */
    val more: List<Pair<String, String>> = emptyList(),
    /** The DAVx5 account it lives in, or null for a Klean contact. */
    val account: String? = null,
)

data class ContactDetailUiState(val loaded: Boolean = false, val contact: ContactDetail? = null, val contactId: String? = null, val rawId: Long? = null)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repo: ContactRepository,
    private val device: DeviceContacts,
    settings: SettingsRepository,
    days: BirthdayDays,
) : ViewModel() {
    private val contactId: String? = savedState.get<String>("contactId")
    private val rawId: Long? = savedState.get<Long>("rawId")?.takeIf { it >= 0 }
    private val other = MutableStateFlow<ContactDetail?>(null)
    private val otherLoaded = MutableStateFlow(rawId == null)

    private val klean = if (contactId == null) flowOf(null) else repo.observeAll().map { l -> l.firstOrNull { it.id == contactId } }

    val state: StateFlow<ContactDetailUiState> = combine(klean, other, otherLoaded, settings.observe(ContactsKeys.ShowAge), days.days()) { c, o, oLoaded, showAge, today ->
        val detail = c?.let {
            val b = it.birthday
            val next = b?.let { x -> Birthdays.next(x, today) }
            ContactDetail(
                name = it.displayName, org = it.org, phones = it.phoneList, emails = it.emailList, addresses = it.addressList,
                birthday = b, nextBirthday = next, turns = if (showAge && b != null && next != null) Birthdays.ageOn(b, next) else null,
                reminderDays = it.reminderDays, anniversary = it.anniversary, notes = it.notes,
                more = ContactVcf.decodeCustom(it.custom).mapNotNull(ContentLines::parse)
                    .map { l -> l.name.lowercase().replaceFirstChar(Char::uppercaseChar) to ContentLines.unescapeText(l.value) },
            )
        } ?: o?.let { d -> d.copy(nextBirthday = d.birthday?.let { Birthdays.next(it, today) }) }
        ContactDetailUiState(loaded = oLoaded, contact = detail, contactId = contactId, rawId = rawId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContactDetailUiState())

    init {
        refresh()
    }

    /** Reads a DAVx5 contact again (after an edit). */
    fun refresh() {
        val raw = rawId ?: return
        viewModelScope.launch {
            other.value = runCatching { device.get(raw) }.getOrNull()?.let {
                ContactDetail(name = it.displayName, org = it.org, phones = it.phones, emails = it.emails, birthday = it.birthday, notes = it.notes, account = it.accountName)
            }
            otherLoaded.value = true
        }
    }
}
