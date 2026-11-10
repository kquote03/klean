package app.klean.core.sync

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SyncCursorEntity
import app.klean.core.data.device.DeviceName
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.secret.SecretStore
import app.klean.core.data.sync.PairedBackend
import app.klean.core.notifications.Rescheduler
import app.klean.core.ui.module.StatusRefresh
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Why the last attempt failed, shown calmly in Settings › Sync. */
enum class SyncProblem { OFFLINE, SERVER, UNAUTHORIZED }

/** What Settings › Sync shows. */
data class SyncStatus(
    val url: String? = null,
    val lastSyncAt: Long? = null,
    val problem: SyncProblem? = null,
) {
    val paired get() = url != null
}

/** Device-local sync state (DataStore `sync_prefs`). Never synced or backed up; the token is in [SecretStore]. */
@Singleton
class SyncPrefs @Inject constructor(@SyncPrefsStore private val store: DataStore<Preferences>) {
    private object K {
        val url = stringPreferencesKey("url")
        val serverDeviceId = stringPreferencesKey("serverDeviceId")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val problem = stringPreferencesKey("problem")
    }

    val url: Flow<String?> = store.data.map { it[K.url] }
    val status: Flow<SyncStatus> = store.data.map { p ->
        SyncStatus(p[K.url], p[K.lastSyncAt], p[K.problem]?.let { runCatching { SyncProblem.valueOf(it) }.getOrNull() })
    }

    suspend fun serverDeviceId(): String? = store.data.first()[K.serverDeviceId]

    suspend fun setPaired(url: String, serverDeviceId: String) = store.edit {
        it.clear()
        it[K.url] = url
        it[K.serverDeviceId] = serverDeviceId
    }

    suspend fun clear() = store.edit { it.clear() }

    suspend fun recordSuccess(at: Long) = store.edit {
        it[K.lastSyncAt] = at
        it.remove(K.problem)
    }

    suspend fun recordProblem(p: SyncProblem) = store.edit { it[K.problem] = p.name }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncPrefsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncHttp

/** Process-wide, like the other DataStores (one instance per file per process). */
internal val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

/** A paired backend: its URL and this phone's bearer token. */
data class SyncAccount(val url: String, val token: String)

enum class PairOutcome { PAIRED, BAD_LINK, BAD_CODE, UNREACHABLE, SERVER_ERROR }

/** Pairing and unpairing; the token lives in [SecretStore.BACKEND_TOKEN]. */
@Singleton
class SyncAccounts @Inject constructor(
    private val prefs: SyncPrefs,
    private val secrets: SecretStore,
    private val devicePrefs: DevicePrefs,
    private val db: KleanDatabase,
    @SyncHttp private val http: HttpClient,
    private val scheduler: SyncScheduler,
    @DeviceName private val deviceName: String,
) : PairedBackend {
    val status: Flow<SyncStatus> = combine(prefs.status, secrets.has(SecretStore.BACKEND_TOKEN)) { s, hasToken ->
        if (hasToken) s else SyncStatus()
    }

    override suspend fun url(): String? = account()?.url

    suspend fun account(): SyncAccount? {
        val url = prefs.url.first() ?: return null
        val token = secrets.get(SecretStore.BACKEND_TOKEN) ?: return null
        return SyncAccount(url, token)
    }

    suspend fun pair(text: String): PairOutcome {
        val link = PairLink.parse(text) ?: return PairOutcome.BAD_LINK
        val claimed = try {
            claimPairing(http, link.url, PairClaimBody(link.code, deviceName, "ANDROID", devicePrefs.deviceId()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncHttpException) {
            return if (e.status == 401 || e.status == 400) PairOutcome.BAD_CODE else PairOutcome.SERVER_ERROR
        } catch (e: Exception) {
            Log.w("Klean", "pairing: server unreachable", e)
            return PairOutcome.UNREACHABLE
        }
        secrets.put(SecretStore.BACKEND_TOKEN, claimed.token)
        prefs.setPaired(link.url, claimed.deviceId)
        // A new server has seen nothing from this phone: push every row, pull everything.
        db.syncCursorDao().upsert(SyncCursorEntity())
        scheduler.schedulePeriodic()
        scheduler.syncNow()
        return PairOutcome.PAIRED
    }

    /** Forgets the backend. Local data stays as it is. The server-side token is revoked when reachable. */
    suspend fun unpair() {
        val acct = account()
        val serverId = prefs.serverDeviceId()
        scheduler.cancel()
        if (acct != null && serverId != null) {
            runCatching { KtorSyncApi(http, acct.url, acct.token).revoke(serverId) }
                .onFailure { Log.w("Klean", "unpair: could not revoke the token on the server") }
        }
        secrets.put(SecretStore.BACKEND_TOKEN, null)
        prefs.clear()
        db.syncCursorDao().upsert(SyncCursorEntity())
    }
}

sealed interface SyncOutcome {
    data object NotPaired : SyncOutcome
    data class Done(val report: SyncReport) : SyncOutcome
    data class Failed(val problem: SyncProblem) : SyncOutcome {
        /** Network and server errors are retried with backoff; a revoked token is not. */
        val retry get() = problem != SyncProblem.UNAUTHORIZED
    }
}

/** Runs the engine for the paired account and records the outcome. Called by [SyncWorker] only. */
@Singleton
class SyncRunner @Inject constructor(
    private val accounts: SyncAccounts,
    private val engine: SyncEngine,
    private val prefs: SyncPrefs,
    @SyncHttp private val http: HttpClient,
    private val clock: Clock,
    private val rescheduler: dagger.Lazy<Rescheduler>,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) {
    suspend fun run(): SyncOutcome {
        val acct = accounts.account() ?: return SyncOutcome.NotPaired
        return try {
            val report = engine.sync(KtorSyncApi(http, acct.url, acct.token))
            prefs.recordSuccess(clock.now().toEpochMilliseconds())
            if (report.applied > 0) {
                // Remote changes bypass the repositories: re-arm reminders and alarms, refresh the surfaces.
                runCatching { rescheduler.get().rescheduleAll() }.onFailure { Log.w("Klean", "sync: reschedule failed", it) }
                statusRefresh.get().refresh()
            }
            SyncOutcome.Done(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncHttpException) {
            val p = if (e.unauthorized) SyncProblem.UNAUTHORIZED else SyncProblem.SERVER
            Log.w("Klean", "sync: ${e.message}")
            prefs.recordProblem(p)
            SyncOutcome.Failed(p)
        } catch (e: Exception) {
            Log.w("Klean", "sync: server unreachable (${e.javaClass.simpleName})")
            prefs.recordProblem(SyncProblem.OFFLINE)
            SyncOutcome.Failed(SyncProblem.OFFLINE)
        }
    }
}
