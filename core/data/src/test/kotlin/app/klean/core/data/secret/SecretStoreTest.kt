package app.klean.core.data.secret

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.backup.BackupManager
import app.klean.core.data.backup.BackupTestRig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reversible stand-in for the Keystore cipher (the Keystore isn't available in Robolectric). */
class FakeSecretCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(42) + plain.map { (it.toInt() xor 0x5A).toByte() }
    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.first() == 42.toByte())
        return blob.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

fun tempSecretStore(): Pair<SecretStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
    val file = File(createTempDirectory("secrets").toFile(), "secrets.preferences_pb")
    val ds = PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { file })
    return SecretStore(ds, FakeSecretCipher()) to ds
}

@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    private val rig = BackupTestRig()

    @After fun close() = rig.db.close()

    @Test fun putGetRoundTripAndCiphertextIsStored() = runBlocking {
        val (store, ds) = tempSecretStore()
        store.put(SecretStore.GEMINI_API_KEY, "AIza-secret")
        assertEquals("AIza-secret", store.get(SecretStore.GEMINI_API_KEY))
        val raw = ds.data.first()[stringPreferencesKey(SecretStore.GEMINI_API_KEY)]
        assertNotEquals("AIza-secret", raw)
        assertFalse(raw!!.contains("AIza-secret"))
        assertTrue(store.has(SecretStore.GEMINI_API_KEY).first())
    }

    @Test fun nullOrBlankRemoves() = runBlocking {
        val (store, _) = tempSecretStore()
        store.put(SecretStore.OPENAI_API_KEY, "k")
        store.put(SecretStore.OPENAI_API_KEY, null)
        assertNull(store.get(SecretStore.OPENAI_API_KEY))
        store.put(SecretStore.OPENAI_API_KEY, "k")
        store.put(SecretStore.OPENAI_API_KEY, "  ")
        assertNull(store.get(SecretStore.OPENAI_API_KEY))
        assertFalse(store.has(SecretStore.OPENAI_API_KEY).first())
    }

    @Test fun unreadableCiphertextReadsAsMissing() = runBlocking {
        val (_, ds) = tempSecretStore()
        val broken = SecretStore(ds, object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain
            override fun decrypt(blob: ByteArray): ByteArray = throw javax.crypto.AEADBadTagException()
        })
        broken.put(SecretStore.GEMINI_API_KEY, "x")
        assertNull(broken.get(SecretStore.GEMINI_API_KEY))
    }

    @Test fun exportAndImportPlain() = runBlocking {
        val (a, _) = tempSecretStore()
        a.put(SecretStore.GEMINI_API_KEY, "g")
        a.put(SecretStore.OPENAI_API_KEY, "o")
        assertEquals(mapOf(SecretStore.GEMINI_API_KEY to "g", SecretStore.OPENAI_API_KEY to "o"), a.exportPlain())
        val (b, _) = tempSecretStore()
        b.put(SecretStore.OPENAI_API_KEY, "old")
        b.importPlain(mapOf(SecretStore.GEMINI_API_KEY to "g2"))
        assertEquals(mapOf(SecretStore.GEMINI_API_KEY to "g2"), b.exportPlain())
    }

    private fun exportWith(store: SecretStore): File {
        val out = ByteArrayOutputStream()
        runBlocking {
            BackupManager(rig.ctx, rig.db, rig.dbFile, rig.photos, rig.prefs, store, rig.buildConfig, rig.clock).exportTo(out)
        }
        return File.createTempFile("exported", ".sqlite", rig.ctx.cacheDir).also { it.writeBytes(out.toByteArray()) }
    }

    private fun secretRows(f: File): List<Pair<String, String>>? = BundledSQLiteDriver().open(f.absolutePath).use { c ->
        val has = c.prepare("SELECT name FROM sqlite_master WHERE name = 'backup_secret'").use { it.step() }
        if (!has) null else c.prepare("SELECT key, value FROM backup_secret ORDER BY key").use { s ->
            buildList { while (s.step()) add(s.getText(0) to s.getText(1)) }
        }
    }

    @Test fun backupIncludesKeysOnlyWhenToggleIsOn() = runBlocking {
        val (store, _) = tempSecretStore()
        store.put(SecretStore.GEMINI_API_KEY, "AIza-1")
        rig.logEntry("e1", "toast", 1_000L)

        assertNull("toggle off: no keys", secretRows(exportWith(store)))

        rig.prefs.setBackupIncludeSecrets(true)
        val withKeys = exportWith(store)
        assertEquals(listOf(SecretStore.GEMINI_API_KEY to "AIza-1"), secretRows(withKeys))

        // Restoring that file puts the key back into a fresh store.
        val (fresh, _) = tempSecretStore()
        BackupManager(rig.ctx, rig.db, rig.dbFile, rig.photos, rig.prefs, fresh, rig.buildConfig, rig.clock)
            .restore(ByteArrayInputStream(withKeys.readBytes()))
        assertEquals("AIza-1", fresh.get(SecretStore.GEMINI_API_KEY))
    }

    @Test fun syncAndAgentTokensAreKeptOutOfBackupsUnlessOptedIn() = runBlocking {
        assertEquals("google.refresh", SecretStore.GOOGLE_REFRESH)
        assertEquals("backend.token", SecretStore.BACKEND_TOKEN)
        assertEquals("hermes.token", SecretStore.HERMES_TOKEN)
        val (store, _) = tempSecretStore()
        store.put(SecretStore.GOOGLE_REFRESH, "1//refresh")
        store.put(SecretStore.BACKEND_TOKEN, "bk")
        store.put(SecretStore.HERMES_TOKEN, "hm")
        assertEquals("1//refresh", store.get(SecretStore.GOOGLE_REFRESH))
        rig.logEntry("e1", "toast", 1_000L)

        assertNull("toggle off: no tokens", secretRows(exportWith(store)))
        rig.prefs.setBackupIncludeSecrets(true)
        assertEquals(
            listOf(SecretStore.BACKEND_TOKEN to "bk", SecretStore.GOOGLE_REFRESH to "1//refresh", SecretStore.HERMES_TOKEN to "hm"),
            secretRows(exportWith(store)),
        )
    }
}
