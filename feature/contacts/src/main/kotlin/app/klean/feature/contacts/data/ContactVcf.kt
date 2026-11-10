package app.klean.feature.contacts.data

import app.klean.core.data.db.entity.ContactEntity
import app.klean.shared.vcard.ContentLines
import app.klean.shared.vcard.VCard
import app.klean.shared.vcard.VCardAddress
import app.klean.shared.vcard.VCardDate
import app.klean.shared.vcard.VCardName
import app.klean.shared.vcard.VCardTyped
import kotlinx.serialization.Serializable

/**
 * Klean contacts ↔ vCard (shared codec, versions 3.0 and 4.0). Labels map to vCard TYPE values; properties Klean
 * doesn't model travel in `contact.custom` as their content lines, so an imported card is exported unchanged.
 */
object ContactVcf {
    @Serializable
    private data class Custom(val vcard: List<String> = emptyList())

    /** Lower-case TYPE values that are not a label. */
    private val NOT_LABELS = setOf("pref", "voice", "internet", "x400")

    fun toDraft(card: VCard): ContactDraft? {
        val name = card.formattedName.trim().ifBlank { card.name?.display().orEmpty().trim() }
            .ifBlank { card.emails.firstOrNull()?.value ?: card.phones.firstOrNull()?.value ?: "" }.trim()
        if (name.isEmpty()) return null
        return ContactDraft(
            displayName = name,
            phones = card.phones.filter { it.value.isNotBlank() }.map { LabeledValue(label(it.types, phone = true), it.value.trim()) },
            emails = card.emails.filter { it.value.isNotBlank() }.map { LabeledValue(label(it.types, phone = false), it.value.trim()) },
            addresses = card.addresses.map {
                PostalAddress(label(it.types, phone = false), it.street, it.locality, it.region, it.postalCode, it.country)
            }.filterNot { it.isBlank() },
            org = card.org?.takeIf { it.isNotBlank() }?.replace(';', ' ')?.trim(),
            notes = card.note?.takeIf { it.isNotBlank() },
            birthday = card.birthday?.let { Birthday.of(it.month, it.day, it.year) },
            name = card.name?.let { StructuredName(it.family, it.given, it.additional, it.prefixes, it.suffixes) },
            anniversary = card.anniversary?.let { d -> Birthday.of(d.month, d.day, d.year)?.let { iso(it) } },
            custom = encodeCustom(card.extras.map { ContentLines.format(it) }),
            uid = card.uid?.takeIf { it.isNotBlank() },
        )
    }

    fun toCard(c: ContactEntity): VCard = VCard(
        uid = c.remoteUid ?: c.id,
        formattedName = c.displayName,
        name = ContactJson.name(c.nameJson)?.let { VCardName(it.family, it.given, it.additional, it.prefix, it.suffix) },
        phones = c.phoneList.map { VCardTyped(types(it.label, phone = true), it.value) },
        emails = c.emailList.map { VCardTyped(types(it.label, phone = false), it.value) },
        addresses = c.addressList.map { VCardAddress(types(it.label, phone = false), it.street, it.locality, it.region, it.postcode, it.country) },
        org = c.org,
        note = c.notes,
        birthday = c.birthday?.let { VCardDate(it.year, it.month, it.day) },
        anniversary = c.anniversary?.let { parseIso(it) }?.let { VCardDate(it.year, it.month, it.day) },
        extras = decodeCustom(c.custom).mapNotNull { ContentLines.parse(it) },
    )

    /** `--MM-DD` or `yyyy-MM-dd`, the format the entity and the provider's Event rows use. */
    fun iso(b: Birthday): String = (b.year?.toString()?.padStart(4, '0') ?: "-") + "-" + two(b.month) + "-" + two(b.day)

    fun parseIso(s: String): Birthday? = VCardDate.parse(s)?.let { Birthday.of(it.month, it.day, it.year) }

    fun encodeCustom(lines: List<String>): String = ContactJson.json.encodeToString(Custom.serializer(), Custom(lines))

    fun decodeCustom(raw: String?): List<String> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { ContactJson.json.decodeFromString(Custom.serializer(), raw).vcard }.getOrDefault(emptyList())

    private fun label(types: List<String>, phone: Boolean): String {
        val t = types.map { it.lowercase() }.filter { it !in NOT_LABELS }
        return when {
            phone && "cell" in t -> "mobile"
            "home" in t -> "home"
            "work" in t -> "work"
            phone && "fax" in t -> "fax"
            else -> t.firstOrNull()?.removePrefix("x-") ?: ""
        }
    }

    private fun types(label: String, phone: Boolean): List<String> = when (val l = label.trim().lowercase()) {
        "" -> emptyList()
        "mobile", "cell" -> if (phone) listOf("cell") else listOf("x-mobile")
        "home", "work", "fax" -> listOf(l)
        else -> listOf(if (l.all { it.isLetterOrDigit() || it == '-' }) l else "x-" + l.filter { it.isLetterOrDigit() })
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')
}
