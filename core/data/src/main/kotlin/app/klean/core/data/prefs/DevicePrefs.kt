package app.klean.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val unlockSheetPretend = booleanPreferencesKey("unlockSheetPretend")
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

    /** "Use wallpaper colours" (Material You). Off: Klean's own pastel scheme. */
    val dynamicColor: Flow<Boolean> = flow(K.dynamicColor, false)
    suspend fun setDynamicColor(v: Boolean) = put(K.dynamicColor, v)

    val lastScreenOffAt: Flow<Long?> = nullable(K.lastScreenOffAt)
    suspend fun setLastScreenOffAt(v: Long?) = put(K.lastScreenOffAt, v)

    /** Debug only: the next wake counts as the end of a long break (unlock sheet). Never exported. */
    val unlockSheetPretend: Flow<Boolean> = flow(K.unlockSheetPretend, false)
    suspend fun setUnlockSheetPretend(v: Boolean) = put(K.unlockSheetPretend, v)

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

    /**
     * This phone owns prompts and rings alarms when several devices sync (default on). Mirrored into
     * the `device` table by `DeviceRepository`; change it through `DeviceRepository.setMainPhone`.
     * Stored under the M10-era key `mainPhone`, so existing installs and backups keep their value.
     */
    val isMainPhone: Flow<Boolean> = flow(K.mainPhone, true)
    suspend fun setIsMainPhone(v: Boolean) = put(K.mainPhone, v)

    /**
     * Device prefs that travel in a backup, as strings. Leaves out what only makes sense on this
     * install: `deviceId`, `lastScreenOffAt`, and the auto-backup folder grant (`autoBackupTreeUri`,
     * plus `autoBackupEnabled`, which is meaningless without that grant).
     */
    suspend fun exportable(): Map<String, String> {
        val p = store.data.first()
        return buildMap { EXPORTABLE.forEach { (key, _) -> p[key]?.let { put(key.name, it.toString()) } } }
    }

    /** Replaces every exportable pref with [values]; missing keys fall back to defaults, unknown or unparsable ones are ignored. */
    suspend fun importAll(values: Map<String, String>) {
        store.edit { p ->
            EXPORTABLE.forEach { (key, parse) ->
                p.remove(key)
                @Suppress("UNCHECKED_CAST")
                values[key.name]?.let(parse)?.let { p[key as Preferences.Key<Any>] = it }
            }
        }
    }

    private companion object {
        private val bool: (String) -> Any? = { it.toBooleanStrictOrNull() }
        private val int: (String) -> Any? = { it.toIntOrNull() }
        private val long: (String) -> Any? = { it.toLongOrNull() }
        private val text: (String) -> Any? = { it }
        val EXPORTABLE: List<Pair<Preferences.Key<*>, (String) -> Any?>> = listOf(
            K.onboardingDone to bool,
            K.dynamicColor to bool,
            K.nextPromptAt to long,
            K.highlightPromptId to text,
            K.autoBackupKeep to int,
            K.backupIncludeSecrets to bool,
            K.aiProviders to text,
            K.aiTaskAssignment to text,
            K.deliveryOffer to text,
            K.mainPhone to bool,
        )
    }

    private fun <T> flow(key: Preferences.Key<T>, default: T): Flow<T> = store.data.map { it[key] ?: default }
    private fun <T> nullable(key: Preferences.Key<T>): Flow<T?> = store.data.map { it[key] }
    private suspend fun <T> put(key: Preferences.Key<T>, value: T?) {
        store.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DevicePrefsStore

/**
 * One DataStore per file per *process*: DataStore throws if two instances open the same file. A Hilt
 * @Singleton is per component, and a process can have several (each instrumented test builds its own),
 * so the instance lives in this process-wide delegate instead.
 */
private val Context.devicePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DevicePrefsModule {
    @Provides
    @DevicePrefsStore
    fun store(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.applicationContext.devicePrefsDataStore
}
