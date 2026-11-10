package app.klean.feature.contacts.data

import android.util.Log
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.db.transact
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.ContactsKeys
import app.klean.shared.id.UuidV7
import app.klean.shared.vcard.VCardCodec
import app.klean.shared.vcard.VCardVersion
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

data class ImportResult(val added: Int, val updated: Int)

/**
 * Told after every contact write, outside the transaction: the provider mirror, birthday reminders and the
 * holistic surfaces follow. One listener failing never stops the others.
 */
fun interface ContactChangeListener {
    suspend fun onContactsChanged(ids: List<String>)
}

/** Klean's own contacts (Room is the source of truth). */
interface ContactRepository {
    fun observeAll(): Flow<List<ContactEntity>>
    /** Every live contact, one-shot (reschedulers, status line, duplicate finder). */
    suspend fun live(): List<ContactEntity>
    suspend fun get(id: String): ContactEntity?
    /** Saves a new contact and returns its id. Throws on a blank name. */
    suspend fun add(draft: ContactDraft): String
    suspend fun update(id: String, draft: ContactDraft)
    /** Tombstones the contact; it keeps syncing as deleted. */
    suspend fun delete(id: String)
    /** Case-insensitive exact name match among live contacts (spaces collapsed). */
    suspend fun findByName(name: String): ContactEntity?
    /** "Sara's birthday is March 3": sets it on the contact called [name], or creates one. Returns the id. */
    suspend fun setBirthday(name: String, birthday: Birthday): String
    suspend fun setReminderDays(id: String, days: List<Int>)
    /** Adds or updates (by UID) every card in [text]. */
    suspend fun importVcf(text: String): ImportResult
    suspend fun exportVcf(version: VCardVersion): String
    suspend fun exportCount(): Int
    /** Folds [dropId] into [keepId] (names, numbers, emails, addresses, birthday) and tombstones [dropId]. */
    suspend fun merge(keepId: String, dropId: String)
    /** Folds what another account knows about the person into [keepId]. */
    suspend fun mergeFrom(keepId: String, other: ContactDraft)
}

@Singleton
class DefaultContactRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val settings: SettingsRepository,
    private val listeners: Set<@JvmSuppressWildcards ContactChangeListener>,
) : ContactRepository {
    private val dao get() = db.contactDao()

    override fun observeAll(): Flow<List<ContactEntity>> = dao.observeAll()
    override suspend fun live(): List<ContactEntity> = dao.live()
    override suspend fun get(id: String): ContactEntity? = dao.get(id)

    override suspend fun add(draft: ContactDraft): String {
        val clean = draft.cleaned()
        val days = clean.reminderDays ?: defaultDays()
        val id = db.transact {
            val rowId = UuidV7.generate(clock.now().toEpochMilliseconds())
            dao.upsert(clean.toEntity(rowId, days, previous = null).copy(sync = stamper.stamp()))
            rowId
        }
        notify(listOf(id))
        return id
    }

    override suspend fun update(id: String, draft: ContactDraft) {
        val clean = draft.cleaned()
        val found = db.transact {
            val prev = dao.get(id)?.takeUnless { it.sync.deleted } ?: return@transact false
            dao.upsert(clean.toEntity(id, clean.reminderDays ?: prev.reminderDays, prev).copy(sync = stamper.stamp(prev.sync)))
            true
        }
        if (found) notify(listOf(id))
    }

    override suspend fun delete(id: String) = change(id) { it.copy(sync = stamper.stamp(it.sync, deleted = true)) }

    override suspend fun findByName(name: String): ContactEntity? {
        val key = normalise(name)
        if (key.isEmpty()) return null
        return dao.live().firstOrNull { normalise(it.displayName) == key }
    }

    override suspend fun setBirthday(name: String, birthday: Birthday): String {
        val existing = findByName(name)
        if (existing == null) return add(ContactDraft(displayName = name, birthday = birthday))
        change(existing.id) {
            it.copy(birthdayMonth = birthday.month, birthdayDay = birthday.day, birthdayYear = birthday.year, sync = stamper.stamp(it.sync))
        }
        return existing.id
    }

    override suspend fun setReminderDays(id: String, days: List<Int>) =
        change(id) { it.copy(birthdayReminders = ReminderDays.encode(days), sync = stamper.stamp(it.sync)) }

    override suspend fun importVcf(text: String): ImportResult {
        val drafts = VCardCodec.parse(text).mapNotNull { ContactVcf.toDraft(it) }
        if (drafts.isEmpty() && text.isNotBlank() && !text.contains("BEGIN:VCARD", ignoreCase = true)) error("not a vCard file")
        val days = defaultDays()
        val changed = mutableListOf<String>()
        var added = 0
        var updated = 0
        db.transact {
            val byUid = dao.live().filter { it.remoteUid != null }.associateBy { it.remoteUid!! }.toMutableMap()
            for (d in drafts) {
                val clean = d.cleaned()
                val prev = clean.uid?.let { byUid[it] } ?: clean.uid?.let { uid -> dao.get(uid)?.takeUnless { it.sync.deleted } }
                if (prev != null) {
                    dao.upsert(clean.toEntity(prev.id, prev.reminderDays, prev).copy(sync = stamper.stamp(prev.sync)))
                    changed += prev.id
                    updated++
                } else {
                    val rowId = UuidV7.generate(clock.now().toEpochMilliseconds())
                    val e = clean.toEntity(rowId, clean.reminderDays ?: days, null).copy(sync = stamper.stamp())
                    dao.upsert(e)
                    e.remoteUid?.let { byUid[it] = e }
                    changed += rowId
                    added++
                }
            }
        }
        notify(changed)
        return ImportResult(added, updated)
    }

    override suspend fun exportVcf(version: VCardVersion): String =
        VCardCodec.writeAll(dao.live().filter { it.sourceAccount == KLEAN }.map { ContactVcf.toCard(it) }, version)

    override suspend fun exportCount(): Int = dao.live().count { it.sourceAccount == KLEAN }

    override suspend fun merge(keepId: String, dropId: String) {
        if (keepId == dropId) return
        val done = db.transact {
            val keep = dao.get(keepId)?.takeUnless { it.sync.deleted } ?: return@transact false
            val drop = dao.get(dropId)?.takeUnless { it.sync.deleted } ?: return@transact false
            val merged = Merge.fold(keep.toDraft(), drop.toDraft())
            dao.upsert(merged.toEntity(keep.id, merged.reminderDays ?: keep.reminderDays, keep).copy(sync = stamper.stamp(keep.sync)))
            dao.upsert(drop.copy(sync = stamper.stamp(drop.sync, deleted = true)))
            true
        }
        if (done) notify(listOf(keepId, dropId))
    }

    override suspend fun mergeFrom(keepId: String, other: ContactDraft) {
        val found = db.transact {
            val keep = dao.get(keepId)?.takeUnless { it.sync.deleted } ?: return@transact false
            val merged = Merge.fold(keep.toDraft(), other.cleaned())
            dao.upsert(merged.toEntity(keep.id, keep.reminderDays, keep).copy(sync = stamper.stamp(keep.sync)))
            true
        }
        if (found) notify(listOf(keepId))
    }

    private suspend fun defaultDays(): List<Int> = ReminderDays.decode(settings.get(ContactsKeys.DefaultReminders))

    private suspend fun change(id: String, edit: suspend (ContactEntity) -> ContactEntity) {
        val found = db.transact {
            val prev = dao.get(id)?.takeUnless { it.sync.deleted } ?: return@transact false
            dao.upsert(edit(prev))
            true
        }
        if (found) notify(listOf(id))
    }

    private suspend fun notify(ids: List<String>) {
        if (ids.isEmpty()) return
        for (l in listeners) {
            try {
                l.onContactsChanged(ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "contact listener failed", e)
            }
        }
    }

    companion object {
        const val KLEAN = "KLEAN"

        /** Lower-case, accents kept, punctuation dropped, whitespace collapsed. */
        fun normalise(name: String): String =
            name.lowercase().replace(Regex("[\\p{Punct}&&[^'-]]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/** Trims every field and drops empty rows. Throws on a blank name. */
fun ContactDraft.cleaned(): ContactDraft {
    val name = displayName.trim().replace(Regex("\\s+"), " ")
    require(name.isNotEmpty()) { "a contact needs a name" }
    fun List<LabeledValue>.clean() = map { LabeledValue(it.label.trim(), it.value.trim()) }.filter { it.value.isNotEmpty() }
    return copy(
        displayName = name,
        phones = phones.clean(),
        emails = emails.clean(),
        addresses = addresses.map {
            it.copy(label = it.label.trim(), street = it.street.trim(), locality = it.locality.trim(), region = it.region.trim(),
                postcode = it.postcode.trim(), country = it.country.trim())
        }.filterNot { it.isBlank() },
        org = org?.trim()?.ifEmpty { null },
        notes = notes?.trim()?.ifEmpty { null },
        reminderDays = reminderDays?.let { ReminderDays.decode(ReminderDays.encode(it)) },
    )
}

/** The row for [id]. Columns the draft doesn't carry (photo, provider id, source account) come from [previous]. */
internal fun ContactDraft.toEntity(id: String, days: List<Int>, previous: ContactEntity?): ContactEntity {
    // A renamed contact drops a structured name that no longer matches.
    val keepName = name ?: ContactJson.name(previous?.nameJson)?.takeIf { previous?.displayName == displayName }
    return ContactEntity(
        id = id,
        displayName = displayName,
        phones = ContactJson.values(phones),
        emails = ContactJson.values(emails),
        notes = notes,
        birthdayMonth = birthday?.month,
        birthdayDay = birthday?.day,
        birthdayYear = birthday?.year,
        birthdayReminders = ReminderDays.encode(days),
        providerRawId = previous?.providerRawId,
        nameJson = ContactJson.name(keepName),
        addresses = ContactJson.addresses(addresses),
        org = org,
        photoBlob = previous?.photoBlob,
        anniversary = anniversary ?: previous?.anniversary,
        custom = custom ?: previous?.custom ?: "{}",
        sourceAccount = previous?.sourceAccount ?: DefaultContactRepository.KLEAN,
        remoteUid = uid ?: previous?.remoteUid,
        sync = previous?.sync ?: app.klean.core.data.db.entity.SyncColumns(0, 0, "", false, 0),
    )
}

/** Folding two records of one person into one. */
object Merge {
    fun fold(keep: ContactDraft, other: ContactDraft): ContactDraft {
        fun digits(s: String) = s.filter { it.isDigit() }.takeLast(9)
        val phones = (keep.phones + other.phones).distinctBy { digits(it.value).ifEmpty { it.value } }
        val emails = (keep.emails + other.emails).distinctBy { it.value.lowercase() }
        val addresses = (keep.addresses + other.addresses).distinctBy { it.oneLine().lowercase() }
        val notes = listOfNotNull(keep.notes, other.notes).distinct().joinToString("\n").ifEmpty { null }
        return keep.copy(
            phones = phones, emails = emails, addresses = addresses,
            org = keep.org ?: other.org, notes = notes,
            // A birthday with a year beats one without.
            birthday = when {
                keep.birthday == null -> other.birthday
                keep.birthday.year == null && other.birthday != null && other.birthday.month == keep.birthday.month &&
                    other.birthday.day == keep.birthday.day -> other.birthday
                else -> keep.birthday
            },
            name = keep.name ?: other.name,
            anniversary = keep.anniversary ?: other.anniversary,
        )
    }
}
