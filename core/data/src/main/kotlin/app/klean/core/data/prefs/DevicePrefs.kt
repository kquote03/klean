package app.klean.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.klean.shared.id.UuidV7
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Device-local preferences (DataStore `device_prefs`). Never synced: these describe this phone,
 * not the user's data. Secrets live elsewhere (Keystore-encrypted store, M10).
 */
@Singleton
class DevicePrefs @Inject constructor(
    @DevicePrefsStore private val store: DataStore<Preferences>,
    private val clock: Clock = Clock.System,
) {
    private object K {
        val deviceId = stringPreferencesKey("deviceId")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val lastScreenOffAt = longPreferencesKey("lastScreenOffAt")
        val nextPromptAt = longPreferencesKey("nextPromptAt")
        val highlightPromptId = stringPreferencesKey("highlightPromptId")
        val autoBackupEnabled = booleanPreferencesKey("autoBackupEnabled")
        val autoBackupTreeUri = stringPreferencesKey("autoBackupTreeUri")
        val autoBackupKeep = intPreferencesKey("autoBackupKeep")
        val backupIncludeSecrets = booleanPreferencesKey("backupIncludeSecrets")
        val aiProviders = stringPreferencesKey("aiProviders")
        val aiTaskAssignment = stringPreferencesKey("aiTaskAssignment")
        val deliveryOffer = stringPreferencesKey("deliveryOffer")
        val mainPhone = booleanPreferencesKey("mainPhone")
    }

    /** Stable id of this install. Generated (UUIDv7) and persisted on first use. */
    suspend fun deviceId(): String {
        store.data.first()[K.deviceId]?.let { return it }
        store.edit { p -> if (p[K.deviceId] == null) p[K.deviceId] = UuidV7.generate(clock.now().toEpochMilliseconds()) }
        return store.data.first()[K.deviceId]!!
    }

    val onboardingDone: Flow<Boolean> = flow(K.onboardingDone, false)
    suspend fun setOnboardingDone(v: Boolean) = put(K.onboardingDone, v)

    val dynamicColor: Flow<Boolean> = flow(K.dynamicColor, true)
    suspend fun setDynamicColor(v: Boolean) = put(K.dynamicColor, v)

    val lastScreenOffAt: Flow<Long?> = nullable(K.lastScreenOffAt)
    suspend fun setLastScreenOffAt(v: Long?) = put(K.lastScreenOffAt, v)

    val nextPromptAt: Flow<Long?> = nullable(K.nextPromptAt)
    suspend fun setNextPromptAt(v: Long?) = put(K.nextPromptAt, v)

    val highlightPromptId: Flow<String?> = nullable(K.highlightPromptId)
    suspend fun setHighlightPromptId(v: String?) = put(K.highlightPromptId, v)

    val autoBackupEnabled: Flow<Boolean> = flow(K.autoBackupEnabled, false)
    suspend fun setAutoBackupEnabled(v: Boolean) = put(K.autoBackupEnabled, v)

    val autoBackupTreeUri: Flow<String?> = nullable(K.autoBackupTreeUri)
    suspend fun setAutoBackupTreeUri(v: String?) = put(K.autoBackupTreeUri, v)

    val autoBackupKeep: Flow<Int> = flow(K.autoBackupKeep, 7)
    suspend fun setAutoBackupKeep(v: Int) = put(K.autoBackupKeep, v)

    val backupIncludeSecrets: Flow<Boolean> = flow(K.backupIncludeSecrets, false)
    suspend fun setBackupIncludeSecrets(v: Boolean) = put(K.backupIncludeSecrets, v)

    /** JSON list of provider configs, without keys. */
    val aiProviders: Flow<String?> = nullable(K.aiProviders)
    suspend fun setAiProviders(v: String?) = put(K.aiProviders, v)

    /** JSON map AiTask → provider id. */
    val aiTaskAssignment: Flow<String?> = nullable(K.aiTaskAssignment)
    suspend fun setAiTaskAssignment(v: String?) = put(K.aiTaskAssignment, v)

    /** NOT_OFFERED / SNOOZED_UNTIL:<ms> / DECLINED / ACCEPTED. */
    val deliveryOffer: Flow<String> = flow(K.deliveryOffer, "NOT_OFFERED")
    suspend fun setDeliveryOffer(v: String) = put(K.deliveryOffer, v)

    /** Future: marks the phone that owns prompts when several devices sync. */
    val mainPhone: Flow<Boolean> = flow(K.mainPhone, true)
    suspend fun setMainPhone(v: Boolean) = put(K.mainPhone, v)

    private fun <T> flow(key: Preferences.Key<T>, default: T): Flow<T> = store.data.map { it[key] ?: default }
    private fun <T> nullable(key: Preferences.Key<T>): Flow<T?> = store.data.map { it[key] }
    private suspend fun <T> put(key: Preferences.Key<T>, value: T?) {
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DevicePrefsStore

@Module
@InstallIn(SingletonComponent::class)
object DevicePrefsModule {
    @Provides
    @Singleton
    @DevicePrefsStore
    fun store(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { ctx.preferencesDataStoreFile("device_prefs") })
}
