package app.klean.feature.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.backup.BackupInfo
import app.klean.core.data.backup.BackupInvalid
import app.klean.core.data.backup.BackupOperations
import app.klean.core.data.backup.ProcessRestarter
import app.klean.core.data.backup.auto.AutoBackupScheduler
import app.klean.core.data.prefs.DevicePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

enum class BackupMessage { EXPORTED, EXPORT_FAILED, NOT_A_BACKUP, NEWER_VERSION, DAMAGED }

/** Shown before a restore: what is here now, and what the backup holds. */
data class RestoreConfirm(val currentEntries: Int, val backup: BackupInfo)

data class BackupUiState(
    val includeSecrets: Boolean = false,
    val autoEnabled: Boolean = false,
    val autoFolder: String? = null,
    val keep: Int = 7,
    val busy: Boolean = false,
    val confirm: RestoreConfirm? = null,
    val message: BackupMessage? = null,
)

private data class Transient(val running: Int = 0, val confirm: RestoreConfirm? = null, val message: BackupMessage? = null)

/**
 * Export, restore and the daily backup. Streams come in as openers because the screen owns the
 * ContentResolver; a restore reads the file twice (inspect, then restore).
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val ops: BackupOperations,
    private val prefs: DevicePrefs,
    private val scheduler: AutoBackupScheduler,
    private val restarter: ProcessRestarter,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : ViewModel() {
    private val transient = MutableStateFlow(Transient())
    private var pendingRestore: (() -> InputStream?)? = null

    val state: StateFlow<BackupUiState> = combine(
        prefs.backupIncludeSecrets, prefs.autoBackupEnabled, prefs.autoBackupTreeUri, prefs.autoBackupKeep, transient,
    ) { secrets, auto, folder, keep, t ->
        BackupUiState(secrets, auto, folder, keep, t.running > 0, t.confirm, t.message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    /** `klean-backup-YYYY-MM-DD.sqlite` in the phone's zone. */
    fun exportFileName(): String = "klean-backup-${clock.now().toLocalDateTime(zone.current()).date}.sqlite"

    fun export(open: () -> OutputStream?) = work {
        val message = try {
            (open() ?: throw IOException("No output")).use { ops.exportTo(it) }
            BackupMessage.EXPORTED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupMessage.EXPORT_FAILED
        }
        transient.update { it.copy(message = message) }
    }

    fun pickRestore(open: () -> InputStream?) = work {
        try {
            val info = (open() ?: throw IOException("No input")).use { ops.inspect(it) }
            pendingRestore = open
            transient.update { it.copy(confirm = RestoreConfirm(ops.currentEntryCount(), info)) }
        } catch (e: BackupInvalid) {
            transient.update { it.copy(message = e.reason.toMessage()) }
        } catch (e: IOException) {
            transient.update { it.copy(message = BackupMessage.DAMAGED) }
        }
    }

    fun dismissRestore() {
        pendingRestore = null
        transient.update { it.copy(confirm = null) }
    }

    fun confirmRestore() {
        val open = pendingRestore ?: return
        pendingRestore = null
        transient.update { it.copy(confirm = null) }
        work {
            try {
                (open() ?: throw BackupInvalid(BackupInvalid.Reason.DAMAGED)).use { ops.restore(it) }
                restarter.restart()
            } catch (e: BackupInvalid) {
                // Rejected before anything was replaced: the current data is untouched.
                transient.update { it.copy(message = e.reason.toMessage()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Interrupted mid-swap: only a fresh process sees a consistent state.
                restarter.restart()
            }
        }
    }

    fun consumeMessage() = transient.update { it.copy(message = null) }

    fun setIncludeSecrets(v: Boolean) = work { prefs.setBackupIncludeSecrets(v) }

    /** The user picked (and granted) a folder: turn the daily backup on. */
    fun setAutoFolder(treeUri: String) = work {
        prefs.setAutoBackupTreeUri(treeUri)
        prefs.setAutoBackupEnabled(true)
        scheduler.apply(true)
    }

    fun disableAuto() = work {
        prefs.setAutoBackupEnabled(false)
        scheduler.apply(false)
    }

    fun setKeep(n: Int) = work { prefs.setAutoBackupKeep(n.coerceIn(1, MAX_KEEP)) }

    private fun work(block: suspend () -> Unit) {
        transient.update { it.copy(running = it.running + 1) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.update { it.copy(running = it.running - 1) }
            }
        }
    }

    /** Suspends until no action is running. For tests. */
    internal suspend fun awaitIdle() {
        transient.first { it.running == 0 }
    }

    private fun BackupInvalid.Reason.toMessage() = when (this) {
        BackupInvalid.Reason.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
        BackupInvalid.Reason.NEWER_VERSION -> BackupMessage.NEWER_VERSION
        BackupInvalid.Reason.DAMAGED -> BackupMessage.DAMAGED
    }

    companion object {
        const val MAX_KEEP = 30
    }
}
