package app.klean.feature.settings.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import app.klean.core.common.ZoneProvider
import app.klean.core.data.backup.BackupInfo
import app.klean.core.data.backup.BackupInvalid
import app.klean.core.data.backup.BackupOperations
import app.klean.core.data.prefs.DevicePrefs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private class FakeOps : BackupOperations {
        var info = BackupInfo(1_700_000_000_000L, 1, "0.1.0", 12, 2, false)
        var inspectError: BackupInvalid? = null
        var restoreError: Exception? = null
        var exportError: Exception? = null
        val exported = mutableListOf<Boolean?>()
        val restored = mutableListOf<String>()
        override suspend fun exportTo(out: OutputStream, includeSecrets: Boolean?) {
            exportError?.let { throw it }
            exported += includeSecrets
            out.write("backup".toByteArray())
        }
        override suspend fun inspect(input: InputStream): BackupInfo {
            input.readBytes()
            inspectError?.let { throw it }
            return info
        }
        override suspend fun restore(input: InputStream) {
            restoreError?.let { throw it }
            restored += input.readBytes().decodeToString()
        }
        override suspend fun currentEntryCount() = 5
    }

    private val prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = DevicePrefs(
        PreferenceDataStoreFactory.create(scope = prefsScope) {
            File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
        },
    )
    private val ops = FakeOps()
    private val scheduled = mutableListOf<Boolean>()
    private var restarts = 0
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-08T21:30:00Z")
    }
    private val dubai = object : ZoneProvider {
        override fun current() = TimeZone.of("Asia/Dubai")
    }

    private val store = ViewModelStore()

    private fun vm() = BackupViewModel(ops, prefs, { scheduled += it }, { restarts++ }, clock, dubai)
        .also { store.put("vm-${System.nanoTime()}", it) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() {
        store.clear() // cancels viewModelScope while Main is still the test dispatcher
        prefsScope.cancel()
        Dispatchers.resetMain()
    }

    /** DataStore runs on real threads; wait (in real time) for the view model to finish its work. */
    private suspend fun BackupViewModel.settle() = withContext(Dispatchers.Default) { withTimeout(5_000) { awaitIdle() } }

    private suspend fun BackupViewModel.await(p: (BackupUiState) -> Boolean): BackupUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(p) } }

    @Test fun exportFileNameUsesTheLocalDate() {
        // 21:30 UTC is already the next day in Dubai.
        assertEquals("klean-backup-2026-11-09.sqlite", vm().exportFileName())
    }

    @Test fun exportWritesAndReportsCalmly() = runTest {
        val vm = vm()
        val out = ByteArrayOutputStream()
        vm.export { out }
        assertEquals(BackupMessage.EXPORTED, vm.await { it.message != null }.message)
        assertEquals("backup", out.toString())
        vm.consumeMessage()
        vm.await { it.message == null }

        ops.exportError = java.io.IOException("full")
        vm.export { ByteArrayOutputStream() }
        assertEquals(BackupMessage.EXPORT_FAILED, vm.await { it.message != null }.message)
    }

    @Test fun restoreAsksFirstThenReplacesAndRestarts() = runTest {
        val vm = vm()
        vm.pickRestore { ByteArrayInputStream("the-backup".toByteArray()) }
        val confirm = vm.await { it.confirm != null }.confirm!!
        assertEquals(5, confirm.currentEntries)
        assertEquals(ops.info, confirm.backup)
        assertEquals(0, restarts)
        assertEquals(emptyList<String>(), ops.restored)

        vm.confirmRestore()
        vm.settle()
        assertEquals(listOf("the-backup"), ops.restored)
        assertEquals(1, restarts)
    }

    @Test fun cancellingTheDialogChangesNothing() = runTest {
        val vm = vm()
        vm.pickRestore { ByteArrayInputStream(byteArrayOf(1)) }
        vm.await { it.confirm != null }
        vm.dismissRestore()
        vm.await { it.confirm == null }
        vm.confirmRestore() // nothing pending any more
        vm.settle()
        assertEquals(emptyList<String>(), ops.restored)
        assertEquals(0, restarts)
    }

    @Test fun invalidBackupsExplainWhyAndNeverAsk() = runTest {
        val vm = vm()
        ops.inspectError = BackupInvalid(BackupInvalid.Reason.NEWER_VERSION)
        vm.pickRestore { ByteArrayInputStream(byteArrayOf(1)) }
        val shown = vm.await { it.message != null }
        assertEquals(BackupMessage.NEWER_VERSION, shown.message)
        assertNull(shown.confirm)
        vm.consumeMessage()

        ops.inspectError = BackupInvalid(BackupInvalid.Reason.NOT_A_BACKUP)
        vm.pickRestore { ByteArrayInputStream(byteArrayOf(1)) }
        assertEquals(BackupMessage.NOT_A_BACKUP, vm.await { it.message != null }.message)
    }

    @Test fun restoreRejectedBeforeTheSwapDoesNotRestart() = runTest {
        val vm = vm()
        vm.pickRestore { ByteArrayInputStream(byteArrayOf(1)) }
        vm.await { it.confirm != null }
        ops.restoreError = BackupInvalid(BackupInvalid.Reason.DAMAGED)
        vm.confirmRestore()
        assertEquals(BackupMessage.DAMAGED, vm.await { it.message != null }.message)
        assertEquals(0, restarts)
    }

    @Test fun restoreInterruptedMidSwapStillRestarts() = runTest {
        val vm = vm()
        vm.pickRestore { ByteArrayInputStream(byteArrayOf(1)) }
        vm.await { it.confirm != null }
        ops.restoreError = java.io.IOException("disk")
        vm.confirmRestore()
        vm.settle()
        assertEquals(1, restarts)
    }

    @Test fun autoBackupFolderEnablesAndSchedulesAndKeepIsBounded() = runTest {
        val vm = vm()
        vm.setAutoFolder("content://tree/a")
        vm.settle()
        assertTrue(prefs.autoBackupEnabled.first())
        assertEquals("content://tree/a", prefs.autoBackupTreeUri.first())
        assertEquals(listOf(true), scheduled)

        vm.setKeep(0)
        vm.settle()
        assertEquals(1, prefs.autoBackupKeep.first())
        vm.setKeep(99)
        vm.settle()
        assertEquals(BackupViewModel.MAX_KEEP, prefs.autoBackupKeep.first())

        vm.disableAuto()
        vm.settle()
        assertFalse(prefs.autoBackupEnabled.first())
        assertEquals(listOf(true, false), scheduled)
    }

    @Test fun includeKeysToggleIsStored() = runTest {
        val vm = vm()
        vm.setIncludeSecrets(true)
        vm.settle()
        assertTrue(prefs.backupIncludeSecrets.first())
    }
}
