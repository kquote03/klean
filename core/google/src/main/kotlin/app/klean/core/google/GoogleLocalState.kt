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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * One queued edit of a Google task, as the full wanted state of the fields Klean may change (title, notes,
 * due date, done). Replayed as a PATCH; a newer edit of the same task replaces an older one (last write wins).
 * [account] is the Google account whose task it is (null: queued before Klean knew account emails). An edit is
 * only ever sent while its account is the current one, so one account's edits never reach another.
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
    val account: String? = null,
)

/**
 * A Google account Klean was authorized with on this phone: profile data only (OpenID `userinfo`), never a token.
 * [pictureFile] is the downloaded photo's file name in the app-private account folder ([GoogleAccounts]).
 */
@Serializable
data class GoogleAccount(
    val email: String,
    val name: String? = null,
    val givenName: String? = null,
    val pictureUrl: String? = null,
    val pictureFile: String? = null,
    /** When the profile (and photo) was last fetched, epoch ms. */
    val profileAt: Long = 0,
) {
    /** The letter on the coloured circle when there is no photo: the first name's, else the email's. */
    val initial: String get() = accountInitial(givenName ?: name, email)

    /** For "Hi, {first name}": the given name, else the full name's first word, else the email's local part. */
    val firstName: String
        get() = givenName?.trim()?.takeIf { it.isNotEmpty() }
            ?: name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
            ?: email.substringBefore('@')
}

/** The first letter or digit of [name], else of [email], upper-cased; "" when there is none. */
fun accountInitial(name: String?, email: String?): String {
    val source = name?.trim()?.takeIf { it.isNotEmpty() } ?: email?.trim().orEmpty()
    val first = source.firstOrNull { it.isLetterOrDigit() } ?: return ""
    return first.uppercase()
}

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class GoogleStore

/** Process-wide singleton (two DataStores on one file crash). */
internal val Context.googleDataStore: DataStore<Preferences> by preferencesDataStore(name = "google")

/**
 * Device-local Google state (DataStore `google`; never synced to the Klean server and never in backups):
 * connected flag, last sync time, the offline outbox (per account), the accounts Klean was authorized with and
 * which one is current, and each account's chosen lists. Tokens are never stored here.
 */
class GoogleLocalState(private val store: DataStore<Preferences>) {
    private object K {
        val connected = booleanPreferencesKey("connected")
        val lastSyncAt = longPreferencesKey("lastSyncAt")
        val outbox = stringPreferencesKey("outbox")
        val accounts = stringPreferencesKey("accounts")
        val current = stringPreferencesKey("currentAccount")
        val selections = stringPreferencesKey("listSelections")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(OutboxOp.serializer())
    private val accountsSer = ListSerializer(GoogleAccount.serializer())
    private val selectionsSer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), Boolean.serializer()))

    val connected: Flow<Boolean> = store.data.map { it[K.connected] == true }
    val lastSyncAt: Flow<Long?> = store.data.map { it[K.lastSyncAt] }

    suspend fun setConnected(v: Boolean) = store.edit { it[K.connected] = v }
    suspend fun setLastSyncAt(at: Long) = store.edit { it[K.lastSyncAt] = at }

    // Accounts

    val accounts: Flow<List<GoogleAccount>> = store.data.map { decodeAccounts(it[K.accounts]) }
    val currentEmail: Flow<String?> = store.data.map { it[K.current] }

    suspend fun currentEmail(): String? = store.data.first()[K.current]
    suspend fun accounts(): List<GoogleAccount> = decodeAccounts(store.data.first()[K.accounts])

    /** Adds [account], or replaces the entry with the same email (keeping its place in the list). */
    suspend fun upsertAccount(account: GoogleAccount) = store.edit { p ->
        val all = decodeAccounts(p[K.accounts])
        val next = if (all.any { it.email == account.email }) all.map { if (it.email == account.email) account else it } else all + account
        p[K.accounts] = json.encodeToString(accountsSer, next)
    }

    suspend fun setCurrent(email: String?) = store.edit { p -> if (email == null) p.remove(K.current) else p[K.current] = email }

    /** Forgets every account, the current one and every account's chosen lists (Disconnect). */
    suspend fun clearAccounts() = store.edit {
        it.remove(K.accounts)
        it.remove(K.current)
        it.remove(K.selections)
    }

    /** The lists [email] chose (remote list id → shown); empty when it never chose, or for no account. */
    suspend fun selections(email: String?): Map<String, Boolean> =
        email?.let { decodeSelections(store.data.first()[K.selections])[it] }.orEmpty()

    /** Merges [selections] into [email]'s chosen lists. */
    suspend fun saveSelections(email: String, selections: Map<String, Boolean>) = store.edit { p ->
        val all = decodeSelections(p[K.selections])
        p[K.selections] = json.encodeToString(selectionsSer, all + (email to (all[email].orEmpty() + selections)))
    }

    // Outbox, per account

    /** The current account's queued edits. Other accounts' edits wait until that account is current again. */
    suspend fun outbox(): List<OutboxOp> = store.data.first().let { p -> decode(p[K.outbox]).filter { it.account == p[K.current] } }
    val outboxFlow: Flow<List<OutboxOp>> = store.data.map { p -> decode(p[K.outbox]).filter { it.account == p[K.current] } }

    /** Every account's queued edits. */
    suspend fun allOutbox(): List<OutboxOp> = decode(store.data.first()[K.outbox])

    /** Queues [op] for the current account, replacing any older op of that account for the same task. */
    suspend fun enqueue(op: OutboxOp) = store.edit { p ->
        val tagged = op.copy(account = p[K.current])
        val rest = decode(p[K.outbox]).filterNot { it.remoteTaskId == op.remoteTaskId && it.account == tagged.account }
        p[K.outbox] = json.encodeToString(listSer, rest + tagged)
    }

    /** Drops [op] once replayed, unless a newer edit of the same task replaced it meanwhile. */
    suspend fun remove(op: OutboxOp) = store.edit { p ->
        val rest = decode(p[K.outbox]).filterNot { it.remoteTaskId == op.remoteTaskId && it.queuedAt == op.queuedAt && it.account == op.account }
        p[K.outbox] = json.encodeToString(listSer, rest)
    }

    /** Edits queued before Klean knew account emails belong to the first account it identifies. */
    suspend fun claimUntaggedOutbox(email: String) = store.edit { p ->
        p[K.outbox] = json.encodeToString(listSer, decode(p[K.outbox]).map { if (it.account == null) it.copy(account = email) else it })
    }

    /** Every account's queued edits (Disconnect). */
    suspend fun clearOutbox() = store.edit { it.remove(K.outbox) }

    private fun decode(s: String?): List<OutboxOp> =
        s?.let { runCatching { json.decodeFromString(listSer, it) }.getOrNull() }.orEmpty()

    private fun decodeAccounts(s: String?): List<GoogleAccount> =
        s?.let { runCatching { json.decodeFromString(accountsSer, it) }.getOrNull() }.orEmpty()

    private fun decodeSelections(s: String?): Map<String, Map<String, Boolean>> =
        s?.let { runCatching { json.decodeFromString(selectionsSer, it) }.getOrNull() }.orEmpty()
}
