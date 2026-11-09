package app.klean.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePrefsTest {
    private val dir: File = createTempDirectory("prefs").toFile()
    private val file = File(dir, "device_prefs.preferences_pb")

    private fun open(scope: CoroutineScope) =
        DevicePrefs(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))

    @Test fun deviceIdIsStableAndSurvivesNewInstance() = runBlocking {
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = open(scope1)
        val first = prefs.deviceId()
        assertEquals(first, prefs.deviceId())
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(first))
        // DataStore releases its file only when the scope's job *completes*; cancel() just requests
        // that. Reopening the same file before then throws "multiple DataStores active for the same
        // file" — on a loaded CI runner about 1 in 7 times. Wait for the old store to shut down.
        scope1.coroutineContext.job.cancelAndJoin()

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        assertEquals(first, open(scope2).deviceId())
        scope2.cancel()
    }

    @Test fun typedAccessorsHaveDefaultsAndPersist() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = open(scope)
        assertFalse(prefs.onboardingDone.first())
        assertEquals(7, prefs.autoBackupKeep.first())
        assertEquals(null, prefs.nextPromptAt.first())
        prefs.setOnboardingDone(true)
        prefs.setNextPromptAt(1234L)
        prefs.setNextPromptAt(null)
        assertTrue(prefs.onboardingDone.first())
        assertEquals(null, prefs.nextPromptAt.first())
        scope.cancel()
    }

    /** "Use wallpaper colours" is opt-in: Klean's own pastel scheme until the user turns it on. */
    @Test fun wallpaperColoursAreOffByDefault() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = open(scope)
        assertFalse(prefs.dynamicColor.first())
        prefs.setDynamicColor(true)
        assertTrue(prefs.dynamicColor.first())
        scope.cancel()
    }

    @Test fun exportImportCarriesTypedPrefsButNotPerInstallOnes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = open(scope)
        val id = prefs.deviceId()
        prefs.setDynamicColor(false)
        prefs.setAutoBackupKeep(3)
        prefs.setNextPromptAt(42L)
        prefs.setAutoBackupEnabled(true)
        prefs.setAutoBackupTreeUri("content://tree")
        prefs.setLastScreenOffAt(5L)
        val exported = prefs.exportable()
        assertEquals(mapOf("dynamicColor" to "false", "autoBackupKeep" to "3", "nextPromptAt" to "42"), exported)

        prefs.setOnboardingDone(true) // not in the backup, so import resets it to its default
        prefs.importAll(exported + ("mainPhone" to "not-a-boolean") + ("unknown" to "x"))
        assertFalse(prefs.dynamicColor.first())
        assertEquals(3, prefs.autoBackupKeep.first())
        assertEquals(42L, prefs.nextPromptAt.first())
        assertFalse(prefs.onboardingDone.first())
        assertTrue(prefs.mainPhone.first())
        assertEquals(id, prefs.deviceId())
        assertTrue(prefs.autoBackupEnabled.first())
        assertEquals("content://tree", prefs.autoBackupTreeUri.first())
        assertEquals(5L, prefs.lastScreenOffAt.first())
        scope.cancel()
    }
}
