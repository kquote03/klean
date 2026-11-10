package app.klean.feature.contacts.device

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.ContactVcf
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.mirror.ContactRows
import app.klean.feature.contacts.mirror.withValues
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A contact that lives in another account on the phone (DAVx5), read and edited through the provider. */
data class DeviceContact(
    val rawId: Long,
    val accountName: String,
    val displayName: String,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val birthday: Birthday? = null,
    val org: String? = null,
    val notes: String? = null,
) {
    fun toDraft() = ContactDraft(displayName = displayName, phones = phones, emails = emails, birthday = birthday, org = org, notes = notes)
}

/** The DAVx5 accounts on this phone. */
object DavAccounts {
    /** DAVx5's main account type. */
    const val TYPE = "bitfire.at.davdroid"
    /** DAVx5 4.x keeps each address book as its own account of this type; the contacts live there. */
    const val ADDRESS_BOOK_TYPE = "bitfire.at.davdroid.address_book"
    val CONTACT_TYPES = listOf(TYPE, ADDRESS_BOOK_TYPE)

    /**
     * True when a DAVx5 account syncs this user's own Klean backend, so its rows can be the provider representation
     * instead of the Klean account's mirror.
     * TODO(M25): compare the DAVx5 account's base URL (AccountManager user data) with the paired backend URL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun syncsOwnBackend(ctx: Context): Boolean = false
}

/** The phone's other-account contacts, behind an interface so screens and merges are tested with a fake. */
interface DeviceContacts {
    /** READ_CONTACTS is granted. */
    fun canRead(): Boolean
    fun canWrite(): Boolean
    /** Names of the DAVx5 accounts (main accounts, or address books when only those are visible). */
    suspend fun davAccounts(): List<String>
    /** Every contact in a DAVx5 account. */
    suspend fun contacts(): List<DeviceContact>
    suspend fun get(rawId: Long): DeviceContact?
    /** Writes the name, numbers, emails, birthday, company and notes back; DAVx5 then syncs the change. */
    suspend fun update(rawId: Long, draft: ContactDraft)
}

@Singleton
class AndroidDeviceContacts @Inject constructor(@ApplicationContext private val ctx: Context) : DeviceContacts {
    private val cr get() = ctx.contentResolver

    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    override fun canRead(): Boolean = granted(Manifest.permission.READ_CONTACTS)
    override fun canWrite(): Boolean = canRead() && granted(Manifest.permission.WRITE_CONTACTS)

    override suspend fun davAccounts(): List<String> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val am = AccountManager.get(ctx)
        val main = runCatching { am.getAccountsByType(DavAccounts.TYPE).map { it.name } }.getOrDefault(emptyList())
        // Accounts of another app are not always visible through AccountManager; their rows are.
        val fromRows = cr.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME),
            "${RawContacts.ACCOUNT_TYPE} IN (?, ?) AND ${RawContacts.DELETED} = 0", DavAccounts.CONTACT_TYPES.toTypedArray(), null,
        )?.use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let(::add) } }.orEmpty()
        (main + fromRows).distinct().sorted()
    }

    override suspend fun contacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        read("${RawContacts.ACCOUNT_TYPE} IN (?, ?)", DavAccounts.CONTACT_TYPES.toTypedArray())
            .ifEmpty { readByRawIds(davRawIds()) }
    }

    override suspend fun get(rawId: Long): DeviceContact? = withContext(Dispatchers.IO) {
        if (!canRead()) null else readByRawIds(mapOf(rawId to (accountOf(rawId) ?: ""))).firstOrNull()
    }

    override suspend fun update(rawId: Long, draft: ContactDraft) = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext
        // Not as a sync adapter: the provider marks the row dirty and DAVx5 uploads it.
        val kinds = listOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE)
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?, ?, ?, ?)", arrayOf(rawId.toString()) + kinds).build()
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
                arrayOf(rawId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())).build()
        val entity = app.klean.core.data.db.entity.ContactEntity(
            id = "", displayName = draft.displayName,
            phones = app.klean.feature.contacts.data.ContactJson.values(draft.phones),
            emails = app.klean.feature.contacts.data.ContactJson.values(draft.emails),
            notes = draft.notes, birthdayMonth = draft.birthday?.month, birthdayDay = draft.birthday?.day, birthdayYear = draft.birthday?.year,
            org = draft.org, sync = app.klean.core.data.db.entity.SyncColumns(0, 0, "", false, 0),
        )
        ContactRows.data(entity).forEach { r ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, rawId).withValue(Data.MIMETYPE, r.mimeType).withValues(r.values).build()
        }
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
        Unit
    }

    private fun accountOf(rawId: Long): String? = cr.query(
        RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME), "${RawContacts._ID} = ?", arrayOf(rawId.toString()), null,
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun davRawIds(): Map<Long, String> = cr.query(
        RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME),
        "${RawContacts.ACCOUNT_TYPE} IN (?, ?) AND ${RawContacts.DELETED} = 0", DavAccounts.CONTACT_TYPES.toTypedArray(), null,
    )?.use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1).orEmpty()) } }.orEmpty()

    private fun readByRawIds(ids: Map<Long, String>): List<DeviceContact> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.keys.joinToString(",")
        return read("${Data.RAW_CONTACT_ID} IN ($list)", null, ids)
    }

    private fun read(selection: String, args: Array<String>?, accounts: Map<Long, String>? = null): List<DeviceContact> {
        val proj = arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, RawContacts.ACCOUNT_NAME)
        val byRaw = linkedMapOf<Long, DeviceContact>()
        val rows = runCatching { cr.query(Data.CONTENT_URI, proj, selection, args, null) }.getOrNull() ?: return emptyList()
        rows.use { c ->
            while (c.moveToNext()) {
                val raw = c.getLong(0)
                val mime = c.getString(1) ?: continue
                val d1 = c.getString(2).orEmpty()
                val type = c.getString(3)?.toIntOrNull()
                val label = c.getString(4).orEmpty()
                val account = runCatching { c.getString(5) }.getOrNull() ?: accounts?.get(raw).orEmpty()
                val cur = byRaw[raw] ?: DeviceContact(raw, account, "")
                byRaw[raw] = when (mime) {
                    StructuredName.CONTENT_ITEM_TYPE -> cur.copy(displayName = d1)
                    Phone.CONTENT_ITEM_TYPE -> cur.copy(phones = cur.phones + LabeledValue(phoneLabel(type, label), d1))
                    Email.CONTENT_ITEM_TYPE -> cur.copy(emails = cur.emails + LabeledValue(emailLabel(type, label), d1))
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) cur.copy(birthday = ContactVcf.parseIso(d1)) else cur
                    Organization.CONTENT_ITEM_TYPE -> cur.copy(org = d1.ifBlank { null })
                    Note.CONTENT_ITEM_TYPE -> cur.copy(notes = d1.ifBlank { null })
                    else -> cur
                }
            }
        }
        return byRaw.values.filter { it.displayName.isNotBlank() }.sortedBy { it.displayName.lowercase() }
    }

    private fun phoneLabel(type: Int?, label: String) = when (type) {
        Phone.TYPE_MOBILE -> "mobile"
        Phone.TYPE_HOME -> "home"
        Phone.TYPE_WORK -> "work"
        Phone.TYPE_CUSTOM -> label
        else -> ""
    }

    private fun emailLabel(type: Int?, label: String) = when (type) {
        Email.TYPE_HOME -> "home"
        Email.TYPE_WORK -> "work"
        Email.TYPE_CUSTOM -> label
        else -> ""
    }
}
