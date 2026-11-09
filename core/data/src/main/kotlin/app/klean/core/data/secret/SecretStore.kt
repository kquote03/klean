package app.klean.core.data.secret

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.klean.core.data.backup.SecretBackup
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Encrypts secret values. The real one is Keystore-backed; tests use a reversible fake. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-256-GCM with a non-exportable Android Keystore key. Blob = 12-byte IV + ciphertext/tag. */
class KeystoreSecretCipher @Inject constructor() : SecretCipher {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_BYTES))
        return c.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "klean_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/**
 * API keys, encrypted with a Keystore key, ciphertext in DataStore `secrets`. Never synced, and in a
 * backup only when the user turns on "include keys" (that path goes through [SecretBackup]).
 */
@Singleton
class SecretStore @Inject constructor(
    @SecretsDataStore private val ds: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SecretBackup {

    suspend fun put(name: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        val encoded = clean?.let { Base64.getEncoder().encodeToString(cipher.encrypt(it.encodeToByteArray())) }
        ds.edit { p ->
            val k = stringPreferencesKey(name)
            if (encoded == null) p.remove(k) else p[k] = encoded
        }
    }

    /** The plain value, or null if unset or unreadable (e.g. the Keystore key was lost). */
    suspend fun get(name: String): String? {
        val b64 = ds.data.first()[stringPreferencesKey(name)] ?: return null
        return decode(b64)
    }

    /** Whether a value is stored, without decrypting it. */
    fun has(name: String): Flow<Boolean> = ds.data.map { it[stringPreferencesKey(name)] != null }

    override suspend fun exportPlain(): Map<String, String> {
        val p = ds.data.first()
        return buildMap {
            p.asMap().forEach { (k, v) -> (v as? String)?.let(::decode)?.let { put(k.name, it) } }
        }
    }

    /** Replaces every stored secret with [secrets]. */
    override suspend fun importPlain(secrets: Map<String, String>) {
        val encoded = secrets.mapValues { (_, v) -> Base64.getEncoder().encodeToString(cipher.encrypt(v.encodeToByteArray())) }
        ds.edit { p ->
            p.clear()
            encoded.forEach { (k, v) -> p[stringPreferencesKey(k)] = v }
        }
    }

    private fun decode(b64: String): String? = try {
        cipher.decrypt(Base64.getDecoder().decode(b64)).decodeToString()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Klean", "A stored secret could not be read")
        null
    }

    companion object {
        const val GEMINI_API_KEY = "gemini.apiKey"
        const val OPENAI_API_KEY = "openai.apiKey"
        /** Google OAuth refresh token (Tasks/Calendar sync, M21). Access tokens stay in memory. */
        const val GOOGLE_REFRESH = "google.refresh"
        /** Bearer token for the paired Klean backend (sync, M24/M25). */
        const val BACKEND_TOKEN = "backend.token"
        /** Hermes API server token (delegation, M23). */
        const val HERMES_TOKEN = "hermes.token"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecretsDataStore

/** Process-wide, like device prefs: two DataStores on one file in one process crash. */
private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

@Module
@InstallIn(SingletonComponent::class)
object SecretsDataStoreModule {
    @Provides
    @SecretsDataStore
    fun store(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.applicationContext.secretsDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecretStoreModule {
    @Binds abstract fun cipher(impl: KeystoreSecretCipher): SecretCipher
}
