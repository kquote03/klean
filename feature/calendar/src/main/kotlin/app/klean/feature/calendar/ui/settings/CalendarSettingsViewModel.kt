package app.klean.feature.calendar.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.SourceKind
import app.klean.feature.calendar.ui.CalendarPrefs
import app.klean.feature.calendar.ui.DeviceAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads and writes the files the user picked for ICS import/export (Storage Access Framework URIs). */
interface IcsFiles {
    suspend fun read(uri: String): String
    suspend fun write(uri: String, text: String)
}

class AndroidIcsFiles @Inject constructor(@ApplicationContext private val ctx: Context) : IcsFiles {
    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().decodeToString() } ?: error("unreadable")
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.encodeToByteArray()) } ?: error("unwritable")
    }
}

data class CalendarChoice(val id: String, val name: String, val color: Int?, val selected: Boolean, val klean: Boolean)

sealed interface SettingsMessage {
    data class Imported(val added: Int, val updated: Int) : SettingsMessage
    data class Exported(val count: Int) : SettingsMessage
    data object ImportFailed : SettingsMessage
    data object ExportFailed : SettingsMessage
}

data class CalendarSettingsUiState(
    val calendars: List<CalendarChoice> = emptyList(),
    val canReadDevice: Boolean = false,
    val showTasks: Boolean = true,
    val showReminders: Boolean = true,
    val defaultReminder: Int = 10,
    val message: SettingsMessage? = null,
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val repo: CalendarRepository,
    private val device: DeviceAccess,
    private val prefs: CalendarPrefs,
    private val files: IcsFiles,
) : ViewModel() {
    private val access = MutableStateFlow(device.canRead())
    /** This phone's calendar_source ids (rows from other devices are not listed here). */
    private val localIds = MutableStateFlow(emptySet<String>())
    private val message = MutableStateFlow<SettingsMessage?>(null)

    private data class Show(val tasks: Boolean, val reminders: Boolean, val reminder: Int)

    val state: StateFlow<CalendarSettingsUiState> = combine(
        repo.observeSources(), access, localIds, combine(prefs.showTasks, prefs.showReminders, prefs.defaultReminder, ::Show), message,
    ) { sources, canRead, local, show, msg ->
        val klean = sources.filter { it.kind == SourceKind.KLEAN }
        val phone = sources.filter { it.kind == SourceKind.DEVICE && it.id in local && canRead }
        CalendarSettingsUiState(
            calendars = (klean + phone).map { CalendarChoice(it.id, it.name, it.color, it.selected, it.kind == SourceKind.KLEAN) },
            canReadDevice = canRead, showTasks = show.tasks, showReminders = show.reminders, defaultReminder = show.reminder,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSettingsUiState())

    init {
        viewModelScope.launch {
            repo.ensureKleanSource()
            listPhoneCalendars()
        }
    }

    private suspend fun listPhoneCalendars() {
        val cals = device.calendars()
        repo.syncDeviceSources(cals)
        localIds.value = cals.map { repo.deviceSourceId(it.id) }.toSet()
    }

    fun onAccessResult() {
        access.value = device.canRead()
        viewModelScope.launch {
            device.onAccessChanged()
            listPhoneCalendars()
        }
    }

    fun setSelected(id: String, selected: Boolean) {
        viewModelScope.launch { repo.setSourceSelected(id, selected) }
    }

    fun setShowTasks(on: Boolean) {
        viewModelScope.launch { prefs.setShowTasks(on) }
    }

    fun setShowReminders(on: Boolean) {
        viewModelScope.launch { prefs.setShowReminders(on) }
    }

    fun setDefaultReminder(minutes: Int) {
        viewModelScope.launch { prefs.setDefaultReminder(minutes) }
    }

    fun import(uri: String) {
        viewModelScope.launch {
            message.value = try {
                val r = repo.importIcs(files.read(uri))
                SettingsMessage.Imported(r.added, r.updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "ICS import failed", e)
                SettingsMessage.ImportFailed
            }
        }
    }

    fun export(uri: String) {
        viewModelScope.launch {
            message.value = try {
                files.write(uri, repo.exportIcs())
                SettingsMessage.Exported(repo.exportCount())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "ICS export failed", e)
                SettingsMessage.ExportFailed
            }
        }
    }

    fun messageShown() {
        message.value = null
    }
}
