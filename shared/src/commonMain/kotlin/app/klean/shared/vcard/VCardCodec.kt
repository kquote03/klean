/*
 * vCard 3.0 (RFC 2426) and 4.0 (RFC 6350) codec for the fields Klean models. Every other property is kept
 * verbatim in [VCard.extras], so a card read from another app and written back loses nothing Klean doesn't model
 * (except property groups on modelled fields, e.g. Apple's `item1.TEL`).
 * Used by the backend's CardDAV and by the Android contacts module (VCF import/export).
 */
package app.klean.shared.vcard

enum class VCardVersion(val text: String) { V3("3.0"), V4("4.0") }

data class VCardName(
    val family: String = "",
    val given: String = "",
    val additional: String = "",
    val prefixes: String = "",
    val suffixes: String = "",
) {
    /** "Given Additional Family", for cards without FN. */
    fun display(): String = listOf(prefixes, given, additional, family, suffixes).filter { it.isNotBlank() }.joinToString(" ")
}

/** A TEL/EMAIL with its lower-cased TYPE values (e.g. cell, work, home, pref). */
data class VCardTyped(val types: List<String>, val value: String)

data class VCardAddress(
    val types: List<String> = emptyList(),
    val street: String = "",
    val locality: String = "",
    val region: String = "",
    val postalCode: String = "",
    val country: String = "",
)

/** A date that may have no year (birthdays: `--MMDD`). */
data class VCardDate(val year: Int?, val month: Int, val day: Int) {
    fun toV4(): String = (year?.toString()?.padStart(4, '0') ?: "--") + two(month) + two(day)
    fun toV3(): String = (year?.toString()?.padStart(4, '0') ?: "-") + "-" + two(month) + "-" + two(day)

    companion object {
        private fun two(n: Int) = n.toString().padStart(2, '0')

        /** `19850412`, `1985-04-12`, `1985-04-12T…`, `--0412`, `--04-12`. */
        fun parse(raw: String): VCardDate? {
            val s = raw.trim().substringBefore('T')
            val noYear = Regex("^--(\\d{2})-?(\\d{2})$").find(s)
            if (noYear != null) return of(null, noYear.groupValues[1].toInt(), noYear.groupValues[2].toInt())
            val full = Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})$").find(s) ?: return null
            return of(full.groupValues[1].toInt(), full.groupValues[2].toInt(), full.groupValues[3].toInt())
        }

        private fun of(y: Int?, m: Int, d: Int) = if (m in 1..12 && d in 1..31) VCardDate(y, m, d) else null
    }
}

data class VCard(
    val uid: String? = null,
    val formattedName: String = "",
    val name: VCardName? = null,
    val phones: List<VCardTyped> = emptyList(),
    val emails: List<VCardTyped> = emptyList(),
    val addresses: List<VCardAddress> = emptyList(),
    val org: String? = null,
    val note: String? = null,
    val birthday: VCardDate? = null,
    val anniversary: VCardDate? = null,
    /** Unmodelled properties, verbatim (escaped values). */
    val extras: List<ContentLine> = emptyList(),
)

object VCardCodec {
    private val SKIPPED = setOf("BEGIN", "END", "VERSION", "PRODID", "REV")

    /** Parses every BEGIN:VCARD…END:VCARD block (bulk VCF files hold many). */
    fun parse(text: String): List<VCard> {
        val cards = ArrayList<VCard>()
        var current: MutableList<ContentLine>? = null
        for (line in ContentLines.unfold(text)) {
            val cl = ContentLines.parse(line) ?: continue
            when {
                cl.name == "BEGIN" && cl.value.equals("VCARD", ignoreCase = true) -> current = ArrayList()
                cl.name == "END" && cl.value.equals("VCARD", ignoreCase = true) -> {
                    current?.let { cards += build(it) }
                    current = null
                }
                else -> current?.add(cl)
            }
        }
        return cards
    }

    private fun build(lines: List<ContentLine>): VCard {
        var card = VCard()
        val extras = ArrayList<ContentLine>()
        for (cl in lines) {
            val text = ContentLines.unescapeText(cl.value)
            when (cl.name) {
                "UID" -> card = card.copy(uid = cl.value)
                "FN" -> card = card.copy(formattedName = text)
                "N" -> {
                    val p = ContentLines.splitEscaped(cl.value, ';').map { ContentLines.unescapeText(it) } + List(5) { "" }
                    card = card.copy(name = VCardName(p[0], p[1], p[2], p[3], p[4]))
                }
                "TEL" -> card = card.copy(phones = card.phones + VCardTyped(types(cl), text.removePrefix("tel:")))
                "EMAIL" -> card = card.copy(emails = card.emails + VCardTyped(types(cl), text))
                "ADR" -> {
                    val p = ContentLines.splitEscaped(cl.value, ';').map { ContentLines.unescapeText(it) } + List(7) { "" }
                    card = card.copy(addresses = card.addresses + VCardAddress(types(cl), p[2], p[3], p[4], p[5], p[6]))
                }
                "ORG" -> card = card.copy(org = text)
                "NOTE" -> card = card.copy(note = text)
                "BDAY" -> card = card.copy(birthday = date(cl))
                "ANNIVERSARY" -> card = card.copy(anniversary = date(cl))
                in SKIPPED -> {}
                else -> extras += cl
            }
        }
        if (card.formattedName.isBlank()) card = card.copy(formattedName = card.name?.display().orEmpty())
        return card.copy(extras = extras)
    }

    private fun types(cl: ContentLine): List<String> =
        cl.paramValues("TYPE").flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() } +
            (if (cl.param("PREF") != null && cl.paramValues("TYPE").none { it.equals("pref", true) }) listOf("pref") else emptyList())

    private fun date(cl: ContentLine): VCardDate? {
        val d = VCardDate.parse(cl.value) ?: return null
        val omit = cl.param("X-APPLE-OMIT-YEAR")?.toIntOrNull()
        return if (omit != null && d.year == omit) d.copy(year = null) else d
    }

    fun write(card: VCard, version: VCardVersion = VCardVersion.V4): String = buildString {
        fun line(name: String, value: String, params: List<Pair<String, List<String>>> = emptyList()) {
            append(ContentLines.fold(ContentLines.format(ContentLine(null, name, params, value))))
        }
        fun esc(s: String) = ContentLines.escapeText(s)
        fun typeParam(types: List<String>) = if (types.isEmpty()) emptyList() else listOf("TYPE" to types)

        line("BEGIN", "VCARD")
        line("VERSION", version.text)
        line("PRODID", "-//Klean//Klean//EN")
        card.uid?.let { line("UID", it) }
        line("FN", esc(card.formattedName))
        val n = card.name ?: VCardName(given = card.formattedName).takeIf { version == VCardVersion.V3 }
        n?.let { line("N", listOf(it.family, it.given, it.additional, it.prefixes, it.suffixes).joinToString(";") { p -> esc(p) }) }
        card.phones.forEach { line("TEL", esc(it.value), typeParam(it.types)) }
        card.emails.forEach { line("EMAIL", esc(it.value), typeParam(it.types)) }
        card.addresses.forEach { a ->
            line("ADR", listOf("", "", a.street, a.locality, a.region, a.postalCode, a.country).joinToString(";") { esc(it) }, typeParam(a.types))
        }
        card.org?.let { line("ORG", esc(it)) }
        card.note?.let { line("NOTE", esc(it)) }
        card.birthday?.let { line("BDAY", if (version == VCardVersion.V4) it.toV4() else it.toV3()) }
        card.anniversary?.let { line("ANNIVERSARY", if (version == VCardVersion.V4) it.toV4() else it.toV3()) }
        card.extras.forEach { append(ContentLines.fold(ContentLines.format(it))) }
        line("END", "VCARD")
    }

    fun writeAll(cards: List<VCard>, version: VCardVersion = VCardVersion.V4): String = cards.joinToString("") { write(it, version) }
}
