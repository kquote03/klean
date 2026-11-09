package app.klean.backend.dav

import app.klean.backend.KleanJson
import app.klean.shared.ical.ICalCodec
import app.klean.shared.ical.ICalEvent
import app.klean.shared.ical.ICalTime
import app.klean.shared.sync.SyncRecord
import app.klean.shared.vcard.ContentLines
import app.klean.shared.vcard.VCard
import app.klean.shared.vcard.VCardAddress
import app.klean.shared.vcard.VCardCodec
import app.klean.shared.vcard.VCardDate
import app.klean.shared.vcard.VCardName
import app.klean.shared.vcard.VCardTyped
import app.klean.shared.vcard.VCardVersion
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Maps Klean's `contact` and `event` rows (as synced JSON, snake_case Room columns) to vCard / iCalendar and back.
 * JSON-in-TEXT columns (`phones`, `emails`, `addresses`, `name_json`, `exdates`) are read whether they arrive as a
 * JSON string (Room's TEXT) or a JSON array/object, and written as strings, like Room stores them.
 * Properties Klean doesn't model are kept in `dav_extras` (server-only; see [RecordStore] carry-over).
 */
object DavMapping {
    const val DAV_EXTRAS = "dav_extras"

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    /** A JSON value stored either inline or as a JSON string (Room TEXT). */
    private fun JsonObject.nested(key: String): JsonElement? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.contentOrNull?.let { runCatching { KleanJson.parseToJsonElement(it) }.getOrNull() }
        else -> v
    }

    private fun labelled(json: JsonObject, key: String): List<Pair<String, String>> =
        (json.nested(key) as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val value = o.str("value") ?: return@mapNotNull null
            (o.str("label") ?: "other") to value
        }

    private fun labelsJson(items: List<Pair<String, String>>): String =
        KleanJson.encodeToString(JsonArray.serializer(), buildJsonArray {
            items.forEach { (l, v) -> add(buildJsonObject { put("label", l); put("value", v) }) }
        })

    private val PHONE_TYPES = mapOf("mobile" to "cell", "home" to "home", "work" to "work", "fax" to "fax", "main" to "pref", "pager" to "pager")
    private val NOISE_TYPES = setOf("voice", "pref", "internet", "x400")

    private fun typesFor(label: String, map: Map<String, String>): List<String> =
        if (label == "other") emptyList() else listOf(map[label] ?: label)

    private fun labelFor(types: List<String>, map: Map<String, String>): String {
        val reverse = map.entries.associate { (k, v) -> v to k }
        types.firstOrNull { it in reverse && it != "pref" }?.let { return reverse.getValue(it) }
        return types.firstOrNull { it !in NOISE_TYPES } ?: if ("pref" in types) "main" else "other"
    }

    private fun extrasOf(json: JsonObject) =
        json.str(DAV_EXTRAS)?.let { s -> ContentLines.unfold(s).mapNotNull { ContentLines.parse(it) } }.orEmpty()

    private fun extrasString(lines: List<app.klean.shared.vcard.ContentLine>): String? =
        lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { ContentLines.format(it) }

    // ---- contacts ----

    fun toVCard(record: SyncRecord): VCard {
        val j = record.json
        val name = (j.nested("name_json") as? JsonObject)?.let {
            VCardName(it.str("family").orEmpty(), it.str("given").orEmpty(), it.str("additional").orEmpty(),
                it.str("prefixes").orEmpty(), it.str("suffixes").orEmpty())
        }
        val month = j.int("birthday_month")
        val day = j.int("birthday_day")
        return VCard(
            uid = j.str("remote_uid") ?: record.id,
            formattedName = j.str("display_name").orEmpty(),
            name = name,
            phones = labelled(j, "phones").map { (l, v) -> VCardTyped(typesFor(l, PHONE_TYPES), v) },
            emails = labelled(j, "emails").map { (l, v) -> VCardTyped(typesFor(l, emptyMap()), v) },
            addresses = (j.nested("addresses") as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                VCardAddress(typesFor(o.str("label") ?: "other", emptyMap()), o.str("street").orEmpty(), o.str("locality").orEmpty(),
                    o.str("region").orEmpty(), o.str("postal_code").orEmpty(), o.str("country").orEmpty())
            },
            org = j.str("org"),
            note = j.str("notes"),
            birthday = if (month != null && day != null) VCardDate(j.int("birthday_year"), month, day) else null,
            anniversary = j.str("anniversary")?.let { VCardDate.parse(it) },
            extras = extrasOf(j),
        )
    }

    fun vcardText(record: SyncRecord, version: VCardVersion = VCardVersion.V3): String = VCardCodec.write(toVCard(record), version)

    /** The contact columns a vCard defines, merged over [existing] (so device-local columns survive). */
    fun contactJson(card: VCard, id: String, existing: JsonObject?): JsonObject {
        val base = existing ?: JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>(base)
        out["id"] = JsonPrimitive(id)
        out["display_name"] = JsonPrimitive(card.formattedName)
        out["phones"] = JsonPrimitive(labelsJson(card.phones.map { labelFor(it.types, PHONE_TYPES) to it.value }))
        out["emails"] = JsonPrimitive(labelsJson(card.emails.map { labelFor(it.types, emptyMap()) to it.value }))
        out["notes"] = card.note?.let { JsonPrimitive(it) } ?: JsonNull
        out["birthday_month"] = JsonPrimitive(card.birthday?.month)
        out["birthday_day"] = JsonPrimitive(card.birthday?.day)
        out["birthday_year"] = JsonPrimitive(card.birthday?.year)
        if (!out.containsKey("birthday_reminders")) out["birthday_reminders"] = JsonPrimitive("0,3")
        out["name_json"] = card.name?.let {
            JsonPrimitive(KleanJson.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("family", it.family); put("given", it.given); put("additional", it.additional)
                put("prefixes", it.prefixes); put("suffixes", it.suffixes)
            }))
        } ?: JsonNull
        out["addresses"] = JsonPrimitive(KleanJson.encodeToString(JsonArray.serializer(), buildJsonArray {
            card.addresses.forEach { a ->
                add(buildJsonObject {
                    put("label", labelFor(a.types, emptyMap())); put("street", a.street); put("locality", a.locality)
                    put("region", a.region); put("postal_code", a.postalCode); put("country", a.country)
                })
            }
        }))
        out["org"] = card.org?.let { JsonPrimitive(it) } ?: JsonNull
        out["anniversary"] = card.anniversary?.let { JsonPrimitive(it.toV3()) } ?: JsonNull
        out["remote_uid"] = card.uid?.takeIf { it != id }?.let { JsonPrimitive(it) } ?: JsonNull
        out[DAV_EXTRAS] = extrasString(card.extras)?.let { JsonPrimitive(it) } ?: JsonNull
        return JsonObject(out)
    }

    // ---- events ----

    private fun zoneOf(j: JsonObject): TimeZone = j.str("zone")?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.UTC

    fun toICal(record: SyncRecord): ICalEvent {
        val j = record.json
        val zone = zoneOf(j)
        val allDay = j.bool("all_day") == true
        val start = j.long("start_at") ?: 0L
        val end = j.long("end_at") ?: start
        val exdates = (j.nested("exdates") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.longOrNull }
        return ICalEvent(
            uid = j.str("uid") ?: record.id,
            summary = j.str("title").orEmpty(),
            description = j.str("notes"),
            location = j.str("location"),
            start = if (allDay) ICalTime.dateOf(start, zone) else ICalTime.utc(start),
            end = if (allDay) ICalTime.dateOf(if (end > start) end else start + 86_400_000L, zone) else ICalTime.utc(end),
            rrule = j.str("rrule") ?: j.str("recurrence"),
            exdates = exdates.map { if (allDay) ICalTime.dateOf(it, zone) else ICalTime.utc(it) },
            alarmMinutesBefore = j.int("reminder_minutes"),
            dtstamp = ICalTime.utc(record.updatedAt).value,
            extras = extrasOf(j),
        )
    }

    fun icsText(record: SyncRecord): String = ICalCodec.write(listOf(toICal(record)))

    fun eventJson(e: ICalEvent, id: String, existing: JsonObject?): JsonObject {
        val base = existing ?: JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>(base)
        val zoneId = e.start.tzid?.takeIf { runCatching { TimeZone.of(it) }.isSuccess } ?: base.str("zone") ?: "UTC"
        val zone = TimeZone.of(zoneId)
        out["id"] = JsonPrimitive(id)
        out["title"] = JsonPrimitive(e.summary)
        out["start_at"] = JsonPrimitive(e.start.toEpochMs(zone))
        out["end_at"] = JsonPrimitive(e.endEpochMs(zone))
        out["all_day"] = JsonPrimitive(e.allDay)
        out["zone"] = JsonPrimitive(zoneId)
        out["location"] = e.location?.let { JsonPrimitive(it) } ?: JsonNull
        out["notes"] = e.description?.let { JsonPrimitive(it) } ?: JsonNull
        out["rrule"] = e.rrule?.let { JsonPrimitive(it) } ?: JsonNull
        if (base.containsKey("recurrence")) out["recurrence"] = out.getValue("rrule")
        out["exdates"] = JsonPrimitive(KleanJson.encodeToString(JsonArray.serializer(), buildJsonArray {
            e.exdates.forEach { add(JsonPrimitive(it.toEpochMs(zone))) }
        }))
        out["reminder_minutes"] = JsonPrimitive(e.alarmMinutesBefore)
        out["uid"] = JsonPrimitive(e.uid)
        out[DAV_EXTRAS] = extrasString(e.extras)?.let { JsonPrimitive(it) } ?: JsonNull
        return JsonObject(out)
    }

    /** Sync metadata columns mirrored into the JSON, as every synced row carries them. */
    fun withSyncColumns(json: JsonObject, existing: JsonObject?, updatedAt: Long, deviceId: String, deleted: Boolean): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(json)
        out["created_at"] = existing?.get("created_at") ?: JsonPrimitive(updatedAt)
        out["updated_at"] = JsonPrimitive(updatedAt)
        out["device_id"] = JsonPrimitive(deviceId)
        out["deleted"] = JsonPrimitive(deleted)
        return JsonObject(out)
    }

    @Suppress("unused")
    private fun JsonElement.obj() = jsonObject
}
