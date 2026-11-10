package app.klean.feature.contacts.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.PostalAddress
import app.klean.feature.contacts.data.ReminderDays
import app.klean.feature.contacts.data.toDraft
import app.klean.feature.contacts.device.DeviceContacts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactForm(
    val name: String = "",
    val org: String = "",
    val phones: List<LabeledValue> = listOf(LabeledValue("mobile", "")),
    val emails: List<LabeledValue> = listOf(LabeledValue("", "")),
    val addresses: List<PostalAddress> = emptyList(),
    /** 1–12, or null for no birthday. */
    val month: Int? = null,
    val day: Int? = null,
    /** Free text so the field can be empty; four digits when given. */
    val year: String = "",
    val reminderDays: Set<Int> = ReminderDays.DEFAULT.toSet(),
    val notes: String = "",
)

enum class FormProblem { NAME, BIRTHDAY }

data class ContactEditUiState(
    val form: ContactForm = ContactForm(),
    val isNew: Boolean = true,
    /** Editing a contact in another account (DAVx5): no reminders or addresses, saved through the provider. */
    val isOther: Boolean = false,
    val loaded: Boolean = false,
    val problem: FormProblem? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ContactRepository,
    private val device: DeviceContacts,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val contactId: String? = savedState.get<String>("contactId")
    private val rawId: Long? = savedState.get<Long>("rawId")?.takeIf { it >= 0 }

    private val _state = MutableStateFlow(ContactEditUiState(isNew = contactId == null && rawId == null, isOther = rawId != null))
    val state: StateFlow<ContactEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val form = when {
                contactId != null -> repo.get(contactId)?.takeUnless { it.sync.deleted }?.toDraft()?.let(::formOf)
                rawId != null -> safe { device.get(rawId) }?.toDraft()?.let(::formOf)
                else -> ContactForm(reminderDays = ReminderDays.decode(settings.get(ContactsKeys.DefaultReminders)).toSet())
            } ?: ContactForm()
            _state.update { it.copy(form = form, loaded = true) }
        }
    }

    fun edit(change: (ContactForm) -> ContactForm) = _state.update { it.copy(form = change(it.form), problem = null) }

    fun setPhone(i: Int, v: LabeledValue) = edit { f -> f.copy(phones = f.phones.replace(i, v)) }
    fun addPhone() = edit { it.copy(phones = it.phones + LabeledValue("mobile", "")) }
    fun removePhone(i: Int) = edit { it.copy(phones = it.phones.filterIndexed { j, _ -> j != i }) }
    fun setEmail(i: Int, v: LabeledValue) = edit { f -> f.copy(emails = f.emails.replace(i, v)) }
    fun addEmail() = edit { it.copy(emails = it.emails + LabeledValue("", "")) }
    fun removeEmail(i: Int) = edit { it.copy(emails = it.emails.filterIndexed { j, _ -> j != i }) }
    fun setAddress(i: Int, v: PostalAddress) = edit { f -> f.copy(addresses = f.addresses.replace(i, v)) }
    fun addAddress() = edit { it.copy(addresses = it.addresses + PostalAddress("home")) }
    fun removeAddress(i: Int) = edit { it.copy(addresses = it.addresses.filterIndexed { j, _ -> j != i }) }
    fun setBirthdayMonth(m: Int?) = edit { it.copy(month = m, day = if (m == null) null else it.day ?: 1) }
    fun setBirthdayDay(d: Int) = edit { it.copy(day = d) }
    fun setBirthdayYear(y: String) = edit { it.copy(year = y.filter(Char::isDigit).take(4)) }
    fun toggleReminder(n: Int) = edit { it.copy(reminderDays = if (n in it.reminderDays) it.reminderDays - n else it.reminderDays + n) }

    fun save() {
        val f = _state.value.form
        if (f.name.isBlank()) return _state.update { it.copy(problem = FormProblem.NAME) }
        val birthday = when {
            f.month == null -> null
            else -> Birthday.of(f.month, f.day ?: 1, f.year.toIntOrNull()) ?: return _state.update { it.copy(problem = FormProblem.BIRTHDAY) }
        }
        val draft = ContactDraft(
            displayName = f.name, org = f.org, phones = f.phones, emails = f.emails, addresses = f.addresses,
            birthday = birthday, reminderDays = f.reminderDays.sorted(), notes = f.notes,
        )
        viewModelScope.launch {
            when {
                rawId != null -> safe { device.update(rawId, draft) }
                contactId != null -> repo.update(contactId, draft)
                else -> repo.add(draft)
            }
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = contactId ?: return
        viewModelScope.launch {
            repo.delete(id)
            _state.update { it.copy(done = true) }
        }
    }

    private fun formOf(d: ContactDraft) = ContactForm(
        name = d.displayName, org = d.org.orEmpty(),
        phones = d.phones.ifEmpty { listOf(LabeledValue("mobile", "")) },
        emails = d.emails.ifEmpty { listOf(LabeledValue("", "")) },
        addresses = d.addresses,
        month = d.birthday?.month, day = d.birthday?.day, year = d.birthday?.year?.toString().orEmpty(),
        reminderDays = (d.reminderDays ?: ReminderDays.DEFAULT).toSet(), notes = d.notes.orEmpty(),
    )

    private fun <T> List<T>.replace(i: Int, v: T) = mapIndexed { j, x -> if (j == i) v else x }

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("Klean", "contact edit", e)
        null
    }
}
