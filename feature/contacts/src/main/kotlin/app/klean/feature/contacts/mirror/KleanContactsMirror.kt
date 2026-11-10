package app.klean.feature.contacts.mirror

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import app.klean.core.data.account.KleanAccount
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.notifications.Reschedulable
import app.klean.feature.contacts.data.ContactChangeListener
import app.klean.feature.contacts.data.ContactJson
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.ContactVcf
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.addressList
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.data.emailList
import app.klean.feature.contacts.data.phoneList
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One `ContactsContract.Data` row: its MIMETYPE and columns. */
data class DataRow(val mimeType: String, val values: Map<String, Any?>)

/** The rows the mirror writes. Pure, so they are checked in unit tests. */
object ContactRows {
    fun rawContact(c: ContactEntity, accountName: String, accountType: String): Map<String, Any?> = mapOf(
        RawContacts.ACCOUNT_NAME to accountName,
        RawContacts.ACCOUNT_TYPE to accountType,
        RawContacts.SOURCE_ID to c.id,
    )

    fun data(c: ContactEntity): List<DataRow> = buildList {
        val n = ContactJson.name(c.nameJson)
        add(
            DataRow(
                StructuredName.CONTENT_ITEM_TYPE,
                buildMap {
                    put(StructuredName.DISPLAY_NAME, c.displayName)
                    if (n != null) {
                        put(StructuredName.GIVEN_NAME, n.given.ifBlank { null })
                        put(StructuredName.FAMILY_NAME, n.family.ifBlank { null })
                        put(StructuredName.MIDDLE_NAME, n.additional.ifBlank { null })
                        put(StructuredName.PREFIX, n.prefix.ifBlank { null })
                        put(StructuredName.SUFFIX, n.suffix.ifBlank { null })
                    }
                },
            ),
        )
        c.phoneList.forEach { p ->
            val type = phoneType(p.label)
            add(DataRow(Phone.CONTENT_ITEM_TYPE, mapOf(Phone.NUMBER to p.value, Phone.TYPE to type, Phone.LABEL to customLabel(type, p.label))))
        }
        c.emailList.forEach { e ->
            val type = emailType(e.label)
            add(DataRow(Email.CONTENT_ITEM_TYPE, mapOf(Email.ADDRESS to e.value, Email.TYPE to type, Email.LABEL to customLabel(type, e.label))))
        }
        c.birthday?.let { add(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.START_DATE to ContactVcf.iso(it), Event.TYPE to Event.TYPE_BIRTHDAY))) }
        c.anniversary?.let { add(DataRow(Event.CONTENT_ITEM_TYPE, mapOf(Event.START_DATE to it, Event.TYPE to Event.TYPE_ANNIVERSARY))) }
        c.org?.let { add(DataRow(Organization.CONTENT_ITEM_TYPE, mapOf(Organization.COMPANY to it))) }
        c.addressList.forEach { a ->
            val type = postalType(a.label)
            add(
                DataRow(
                    StructuredPostal.CONTENT_ITEM_TYPE,
                    mapOf(
                        StructuredPostal.STREET to a.street.ifBlank { null }, StructuredPostal.CITY to a.locality.ifBlank { null },
                        StructuredPostal.REGION to a.region.ifBlank { null }, StructuredPostal.POSTCODE to a.postcode.ifBlank { null },
                        StructuredPostal.COUNTRY to a.country.ifBlank { null }, StructuredPostal.TYPE to type,
                        StructuredPostal.LABEL to customLabel(type, a.label),
                    ),
                ),
            )
        }
        c.notes?.let { add(DataRow(Note.CONTENT_ITEM_TYPE, mapOf(Note.NOTE to it))) }
    }

    fun phoneType(label: String): Int = when (label.trim().lowercase()) {
        "mobile", "cell" -> Phone.TYPE_MOBILE
        "home" -> Phone.TYPE_HOME
        "work" -> Phone.TYPE_WORK
        "fax" -> Phone.TYPE_FAX_WORK
        "" -> Phone.TYPE_OTHER
        else -> Phone.TYPE_CUSTOM
    }

    fun emailType(label: String): Int = when (label.trim().lowercase()) {
        "home" -> Email.TYPE_HOME
        "work" -> Email.TYPE_WORK
        "mobile" -> Email.TYPE_MOBILE
        "" -> Email.TYPE_OTHER
        else -> Email.TYPE_CUSTOM
    }

    private fun postalType(label: String): Int = when (label.trim().lowercase()) {
        "home" -> StructuredPostal.TYPE_HOME
        "work" -> StructuredPostal.TYPE_WORK
        "" -> StructuredPostal.TYPE_OTHER
        else -> StructuredPostal.TYPE_CUSTOM
    }

    /** The provider's custom types all share the value 0 (`BaseTypes.TYPE_CUSTOM`). */
    private fun customLabel(type: Int, label: String): String? = if (type == ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM) label else null
}

/** The provider calls the mirror needs, so the mirror is tested without a ContentResolver. */
interface ContactsProviderOps {
    /** READ_CONTACTS and WRITE_CONTACTS are granted. */
    fun canWrite(): Boolean
    /** `SOURCE_ID` (Klean contact id) → `RawContacts._ID` for the Klean account's live rows. */
    fun sourceIds(): Map<String, Long>
    fun insert(raw: Map<String, Any?>, rows: List<DataRow>): Long
    /** Replaces every data row of [rawId]. */
    fun replace(rawId: Long, rows: List<DataRow>)
    fun delete(rawId: Long)
    /** Shows two raw contacts as one card in the phone's contacts and dialer. */
    fun keepTogether(rawA: Long, rawB: Long)
}

/** Live Klean contacts, one-shot. */
class MirrorSource(val get: suspend (String) -> ContactEntity?, val all: suspend () -> List<ContactEntity>)

/**
 * Mirrors Klean's own contacts (Room is the source of truth) as raw contacts under the Klean Android account, so the
 * dialer, messages and other apps see them. Writes as the account's sync adapter, keyed by `SOURCE_ID` = contact id.
 * Runs after every contact write and as a full resync at startup and after boot ([Reschedulable]). Needs
 * READ/WRITE_CONTACTS; without them it does nothing. With the Contacts module off, the rows are removed.
 *
 * When a DAVx5 account syncs this user's own Klean backend (M25: it matches the paired backend), DAVx5's rows are the
 * provider representation: [useDavInstead] is true, mirroring stops and the Klean account's rows are removed. See
 * [app.klean.feature.contacts.device.DavAccounts.syncsOwnBackend].
 */
class KleanContactsMirror(
    private val provider: ContactsProviderOps,
    private val source: MirrorSource,
    private val gate: ModuleGate,
    private val accountName: String,
    private val accountType: String,
    private val ensureAccount: suspend () -> Unit,
    private val useDavInstead: suspend () -> Boolean = { false },
) : ContactChangeListener, Reschedulable {
    private val lock = Mutex()

    private suspend fun mirroring() = gate.isEnabled(ModuleId.CONTACTS) && !useDavInstead()

    override suspend fun onContactsChanged(ids: List<String>) {
        if (ids.isEmpty()) return
        lock.withLock {
            if (!provider.canWrite() || !mirroring()) return
            ensureAccount()
            val existing = provider.sourceIds()
            for (id in ids) {
                val c = source.get(id)
                val row = existing[id]
                when {
                    c == null || c.sync.deleted || c.sourceAccount != DefaultContactRepository.KLEAN -> row?.let(provider::delete)
                    row != null -> provider.replace(row, ContactRows.data(c))
                    else -> provider.insert(ContactRows.rawContact(c, accountName, accountType), ContactRows.data(c))
                }
            }
        }
    }

    override suspend fun reschedule() = lock.withLock {
        if (!provider.canWrite()) return@withLock
        val existing = provider.sourceIds().toMutableMap()
        if (!mirroring()) {
            existing.values.forEach(provider::delete)
            return@withLock
        }
        ensureAccount()
        for (c in source.all()) {
            if (c.sync.deleted || c.sourceAccount != DefaultContactRepository.KLEAN) continue
            val row = existing.remove(c.id)
            if (row != null) provider.replace(row, ContactRows.data(c)) else provider.insert(ContactRows.rawContact(c, accountName, accountType), ContactRows.data(c))
        }
        existing.values.forEach(provider::delete)
    }

    /** The provider row of Klean contact [id], if mirrored. */
    suspend fun rawIdOf(id: String): Long? = lock.withLock { if (provider.canWrite()) provider.sourceIds()[id] else null }
}

/** [ContactsProviderOps] over the real ContentResolver, as the Klean account's sync adapter. */
@Singleton
class AndroidContactsProvider @Inject constructor(@ApplicationContext private val ctx: Context) : ContactsProviderOps {
    private val cr get() = ctx.contentResolver
    private val accountType get() = KleanAccount.accountType(ctx)

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, KleanAccount.NAME)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
        .build()

    override fun canWrite(): Boolean = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        .all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    override fun sourceIds(): Map<String, Long> = cr.query(
        RawContacts.CONTENT_URI.asSyncAdapter(), arrayOf(RawContacts._ID, RawContacts.SOURCE_ID),
        "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.DELETED} = 0",
        arrayOf(accountType, KleanAccount.NAME), null,
    )?.use { c -> buildMap { while (c.moveToNext()) c.getString(1)?.let { put(it, c.getLong(0)) } } }.orEmpty()

    override fun insert(raw: Map<String, Any?>, rows: List<DataRow>): Long {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI.asSyncAdapter()).withValues(raw).build()
        rows.forEach { r ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI.asSyncAdapter())
                .withValueBackReference(Data.RAW_CONTACT_ID, 0).withValue(Data.MIMETYPE, r.mimeType).withValues(r.values).build()
        }
        val res = cr.applyBatch(ContactsContract.AUTHORITY, ops)
        return res.firstOrNull()?.uri?.let(ContentUris::parseId) ?: -1
    }

    override fun replace(rawId: Long, rows: List<DataRow>) {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI.asSyncAdapter())
            .withSelection("${Data.RAW_CONTACT_ID} = ?", arrayOf(rawId.toString())).build()
        rows.forEach { r ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI.asSyncAdapter())
                .withValue(Data.RAW_CONTACT_ID, rawId).withValue(Data.MIMETYPE, r.mimeType).withValues(r.values).build()
        }
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    override fun delete(rawId: Long) {
        cr.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId).asSyncAdapter(), null, null)
    }

    override fun keepTogether(rawA: Long, rawB: Long) {
        val op = ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
            .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
            .withValue(AggregationExceptions.RAW_CONTACT_ID1, rawA)
            .withValue(AggregationExceptions.RAW_CONTACT_ID2, rawB)
            .build()
        cr.applyBatch(ContactsContract.AUTHORITY, arrayListOf(op))
    }
}

internal fun ContentProviderOperation.Builder.withValues(values: Map<String, Any?>): ContentProviderOperation.Builder {
    for ((k, v) in values) withValue(k, v)
    return this
}

/** Builds the mirror from the app's real pieces. */
@Singleton
class KleanContactsMirrorFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val provider: AndroidContactsProvider,
    private val repo: dagger.Lazy<ContactRepository>,
    private val gate: ModuleGate,
    private val backend: dagger.Lazy<app.klean.core.data.sync.PairedBackend>,
) {
    val mirror: KleanContactsMirror by lazy {
        KleanContactsMirror(
            provider,
            MirrorSource({ repo.get().get(it) }, { repo.get().live() }),
            gate,
            accountName = KleanAccount.NAME,
            accountType = KleanAccount.accountType(ctx),
            ensureAccount = { KleanAccount.ensure(ctx) },
            useDavInstead = {
                app.klean.feature.contacts.device.DavAccounts.syncsOwnBackend(
                    ctx, backend.get().url(), repo.get().live().mapTo(HashSet()) { it.id },
                )
            },
        )
    }

    suspend fun <T> onIo(block: suspend KleanContactsMirror.() -> T): T = withContext(Dispatchers.IO) { mirror.block() }
}

/**
 * The mirror as the app wires it: a [ContactChangeListener] after every write and a [Reschedulable] for the full
 * resync (startup, boot, permission granted, module switched on or off). Provider work runs on IO.
 */
@Singleton
class ContactsMirrorHook @Inject constructor(
    private val factory: KleanContactsMirrorFactory,
    private val provider: AndroidContactsProvider,
) : ContactChangeListener, Reschedulable, ProviderJoin {
    override suspend fun onContactsChanged(ids: List<String>) = factory.onIo { onContactsChanged(ids) }
    override suspend fun reschedule() = factory.onIo { reschedule() }

    /** Joins Klean contact [id]'s mirror row with another account's raw contact into one card. */
    override suspend fun keepTogether(id: String, otherRawId: Long) = factory.onIo {
        rawIdOf(id)?.let { runCatching { provider.keepTogether(it, otherRawId) } }
        Unit
    }
}

/** Joins a Klean contact's provider row with another account's raw contact (merges). */
interface ProviderJoin {
    suspend fun keepTogether(id: String, otherRawId: Long)
}
