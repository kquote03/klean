package app.klean.feature.contacts.merge

import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.LabeledValue
import app.klean.feature.contacts.data.emailList
import app.klean.feature.contacts.data.phoneList
import app.klean.feature.contacts.device.DeviceContact
import app.klean.feature.contacts.device.DeviceContacts
import app.klean.feature.contacts.mirror.ProviderJoin
import javax.inject.Inject
import javax.inject.Singleton

/** One record of a person: a Klean contact, or a contact in another account on the phone. */
data class Person(
    val key: String,
    val name: String,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val kleanId: String? = null,
    val rawId: Long? = null,
    val accountName: String? = null,
    val createdAt: Long = 0,
) {
    companion object {
        fun of(c: ContactEntity) = Person("klean:${c.id}", c.displayName, c.phoneList, c.emailList, kleanId = c.id, createdAt = c.sync.createdAt)
        fun of(d: DeviceContact) = Person("raw:${d.rawId}", d.displayName, d.phones, d.emails, rawId = d.rawId, accountName = d.accountName)
    }
}

/** Two records that look like the same person. [a] is the one kept (always a Klean contact). */
data class DuplicatePair(val a: Person, val b: Person) {
    val key: String get() = listOf(a.key, b.key).sorted().joinToString("|")
}

/**
 * "These look like the same people": the same normalised name plus at least one shared phone number (last 9 digits)
 * or email (case-insensitive). Klean ↔ Klean (e.g. a VCF imported twice) and Klean ↔ another account (DAVx5).
 * Two records from other accounts are left to their own app. Pure.
 */
object DuplicateFinder {
    fun find(klean: List<ContactEntity>, device: List<DeviceContact>, keptApart: Set<String> = emptySet()): List<DuplicatePair> {
        val people = klean.filter { !it.sync.deleted && it.sourceAccount == DefaultContactRepository.KLEAN }.map(Person::of) + device.map(Person::of)
        val out = mutableListOf<DuplicatePair>()
        val used = mutableSetOf<String>()
        for ((_, group) in people.groupBy { DefaultContactRepository.normalise(it.name) }.filterKeys { it.isNotEmpty() }) {
            if (group.size < 2) continue
            val sorted = group.sortedWith(compareBy<Person> { it.kleanId == null }.thenBy { it.createdAt })
            for (i in sorted.indices) for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (a.kleanId == null) continue
                if (a.key in used || b.key in used) continue
                if (!shareSomething(a, b)) continue
                val pair = DuplicatePair(a, b)
                if (pair.key in keptApart) continue
                out += pair
                used += b.key
            }
        }
        return out.sortedBy { it.a.name.lowercase() }
    }

    fun phoneKey(s: String): String = s.filter { it.isDigit() }.takeLast(9).takeIf { it.length >= 6 }.orEmpty()

    private fun shareSomething(a: Person, b: Person): Boolean {
        val phones = a.phones.map { phoneKey(it.value) }.filter { it.isNotEmpty() }.toSet()
        if (b.phones.any { phoneKey(it.value) in phones }) return true
        val emails = a.emails.map { it.value.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return b.emails.any { it.value.trim().lowercase() in emails }
    }
}

/** The one-time merge offer: finds the pairs, merges one, keeps one apart, or is set aside for good. */
@Singleton
class MergeOffer @Inject constructor(
    private val repo: ContactRepository,
    private val device: DeviceContacts,
    private val settings: SettingsRepository,
    private val mirror: ProviderJoin,
) {
    /** The pairs to offer, or none once the offer was answered. */
    suspend fun pairs(): List<DuplicatePair> {
        if (settings.get(ContactsKeys.MergeOfferDone)) return emptyList()
        val others = if (device.canRead()) runCatching { device.contacts() }.getOrDefault(emptyList()) else emptyList()
        return DuplicateFinder.find(repo.live(), others, settings.get(ContactsKeys.KeptApart).toSet())
    }

    suspend fun merge(pair: DuplicatePair) {
        val keep = pair.a.kleanId ?: return
        when {
            pair.b.kleanId != null -> repo.merge(keep, pair.b.kleanId)
            pair.b.rawId != null -> {
                device.get(pair.b.rawId)?.let { repo.mergeFrom(keep, it.toDraft()) }
                mirror.keepTogether(keep, pair.b.rawId)
            }
        }
        // The other account still holds its record: never offer this pair again.
        settings.update(ContactsKeys.KeptApart) { (it + pair.key).distinct() }
        finishIfEmpty()
    }

    suspend fun keepApart(pair: DuplicatePair) {
        settings.update(ContactsKeys.KeptApart) { (it + pair.key).distinct() }
        finishIfEmpty()
    }

    /** "Not now": the offer doesn't come back. */
    suspend fun setAside() = settings.set(ContactsKeys.MergeOfferDone, true)

    private suspend fun finishIfEmpty() {
        if (pairs().isEmpty()) setAside()
    }
}
