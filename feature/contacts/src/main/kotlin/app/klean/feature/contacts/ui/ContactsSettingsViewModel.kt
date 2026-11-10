package app.klean.feature.contacts.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.notifications.Reschedulable
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.birthday.BirthdayReminderSync
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.ReminderDays
import app.klean.feature.contacts.device.DeviceContacts
import app.klean.feature.contacts.mirror.ContactsMirrorHook
import app.klean.feature.contacts.today.BirthdaySurfaces
import app.klean.shared.vcard.VCardVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads and writes the files the user picked for VCF import/export (Storage Access Framework URIs). */
interface VcfFiles {
    suspend fun read(uri: String): String
    suspend fun write(uri: String, text: String)
}

class AndroidVcfFiles @Inject constructor(@ApplicationContext private val ctx: Context) : VcfFiles {
    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().decodeToString() } ?: error("unreadable")
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("unwritable")
    }
}

sealed interface ContactsMessage {
    data class Imported(val added: Int, val updated: Int) : ContactsMessage
    data class Exported(val count: Int) : ContactsMessage
    data object ImportFailed : ContactsMessage
    data object ExportFailed : ContactsMessage
}

data class ContactsSettingsUiState(
    val canReadPhone: Boolean = false,
    val davAccounts: List<String> = emptyList(),
    val message: ContactsMessage? = null,
)

/** What follows a contacts permission answer: mirror Klean's contacts if now allowed. */
fun interface ContactsAccessHook {
    suspend fun onAccessChanged()
}

class MirrorAccessHook @Inject constructor(private val mirror: ContactsMirrorHook) : ContactsAccessHook {
    override suspend fun onAccessChanged() = mirror.reschedule()
}

@HiltViewModel
class ContactsSettingsViewModel @Inject constructor(
    private val repo: ContactRepository,
    private val device: DeviceContacts,
    private val files: VcfFiles,
    private val access: ContactsAccessHook,
) : ViewModel() {
    private val canRead = MutableStateFlow(device.canRead())
    private val accounts = MutableStateFlow<List<String>>(emptyList())
    private val message = MutableStateFlow<ContactsMessage?>(null)

    val state: StateFlow<ContactsSettingsUiState> = combine(canRead, accounts, message, ::ContactsSettingsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContactsSettingsUiState())

    init {
        viewModelScope.launch { accounts.value = runCatching { device.davAccounts() }.getOrDefault(emptyList()) }
    }

    fun onAccessResult() {
        canRead.value = device.canRead()
        viewModelScope.launch {
            runCatching { access.onAccessChanged() }
            accounts.value = runCatching { device.davAccounts() }.getOrDefault(emptyList())
        }
    }

    fun import(uri: String) {
        viewModelScope.launch {
            message.value = try {
                val r = repo.importVcf(files.read(uri))
                ContactsMessage.Imported(r.added, r.updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "VCF import failed", e)
                ContactsMessage.ImportFailed
            }
        }
    }

    fun export(uri: String, version: VCardVersion) {
        viewModelScope.launch {
            message.value = try {
                files.write(uri, repo.exportVcf(version))
                ContactsMessage.Exported(repo.exportCount())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "VCF export failed", e)
                ContactsMessage.ExportFailed
            }
        }
    }

    fun messageShown() {
        message.value = null
    }
}

data class BirthdaysSettingsUiState(val showAge: Boolean = true, val defaultDays: Set<Int> = ReminderDays.DEFAULT.toSet(), val hour: Int = 9)

/** Birthday settings; every change re-arms the reminders (titles carry the age, times the hour). */
@HiltViewModel
class BirthdaysSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @Named(BIRTHDAY_RESYNC) private val resync: Reschedulable,
) : ViewModel() {
    val state: StateFlow<BirthdaysSettingsUiState> = combine(
        settings.observe(ContactsKeys.ShowAge), settings.observe(ContactsKeys.DefaultReminders), settings.observe(ContactsKeys.ReminderHour),
    ) { age, days, hour -> BirthdaysSettingsUiState(age, ReminderDays.decode(days).toSet(), hour) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BirthdaysSettingsUiState())

    fun setShowAge(on: Boolean) = change { settings.set(ContactsKeys.ShowAge, on) }
    fun toggleDefault(n: Int) = change {
        settings.update(ContactsKeys.DefaultReminders) { raw ->
            val d = ReminderDays.decode(raw).toSet()
            ReminderDays.encode((if (n in d) d - n else d + n).toList())
        }
    }
    fun setHour(h: Int) = change { settings.set(ContactsKeys.ReminderHour, h.coerceIn(0, 23)) }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            runCatching { resync.reschedule() }
        }
    }

    companion object {
        const val BIRTHDAY_RESYNC = "birthdays.resync"
        /** Hours offered for birthday reminders. */
        val HOURS = listOf(8, 9, 10, 12)
    }
}

/** The birthday reminders and the surfaces again, after a birthday setting changed. */
class BirthdayResync @Inject constructor(private val sync: BirthdayReminderSync, private val surfaces: BirthdaySurfaces) : Reschedulable {
    override suspend fun reschedule() {
        sync.reschedule()
        surfaces.onContactsChanged(emptyList())
    }
}
