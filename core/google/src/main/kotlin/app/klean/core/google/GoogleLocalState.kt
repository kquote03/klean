package app.klean.core.google

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One queued edit of a Google task, as the full wanted state of the fields Klean may change (title, notes,
 * due date, done). Replayed as a PATCH; a newer edit of the same task replaces an older one (last write wins).
 */
@Serializable
data class OutboxOp(
    val taskId: String,
    val remoteListId: String,
    val remoteTaskId: String,
    val title: String,
    val notes: String? = null,
    /** yyyy-MM-dd, or null for no date. Google Tasks keeps dates only, never times. */
    val due: String? = null,
    val done: Boolean = false,
    val queuedAt: Long,
)

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GoogleStore

/** Process-wide singleton (two DataStores on one file crash). */
internal val Context.googleDataStore: DataStore<Preferences> by preferencesDataStore(name = "google")

/**
 * Device-local Google state (DataStore `google`, never synced): connected flag, last sync time and the
 * offline outbox. Tokens are never stored here.
 */
class GoogleLocalState(private val store: DataStore<Preferences>) {
    private object K {
        val connected = booleanPreferencesKey("connected")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val outbox = stringPreferencesKey("outbox")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(OutboxOp.serializer())

    val connected: Flow<Boolean> = store.data.map { it[K.connected] == true }
    val lastSyncAt: Flow<Long?> = store.data.map { it[K.lastSyncAt] }

    suspend fun setConnected(v: Boolean) = store.edit { it[K.connected] = v }
    suspend fun setLastSyncAt(at: Long) = store.edit { it[K.lastSyncAt] = at }

    suspend fun outbox(): List<OutboxOp> = decode(store.data.first()[K.outbox])
    val outboxFlow: Flow<List<OutboxOp>> = store.data.map { decode(it[K.outbox]) }

    /** Queues [op], replacing any older op for the same task. */
    suspend fun enqueue(op: OutboxOp) = store.edit { p ->
        val rest = decode(p[K.outbox]).filterNot { it.remoteTaskId == op.remoteTaskId }
        p[K.outbox] = json.encodeToString(listSer, rest + op)
    }

    /** Drops [op] once replayed, unless a newer edit of the same task replaced it meanwhile. */
    suspend fun remove(op: OutboxOp) = store.edit { p ->
        val rest = decode(p[K.outbox]).filterNot { it.remoteTaskId == op.remoteTaskId && it.queuedAt == op.queuedAt }
        p[K.outbox] = json.encodeToString(listSer, rest)
    }

    suspend fun clearOutbox() = store.edit { it.remove(K.outbox) }

    private fun decode(s: String?): List<OutboxOp> =
        s?.let { runCatching { json.decodeFromString(listSer, it) }.getOrNull() }.orEmpty()
}
