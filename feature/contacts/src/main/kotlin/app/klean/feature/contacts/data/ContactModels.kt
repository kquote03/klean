package app.klean.feature.contacts.data

import app.klean.core.data.db.entity.ContactEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A phone number or email with its label ("mobile", "home", "work", or anything the user typed). */
@Serializable
data class LabeledValue(val label: String = "", val value: String)

/** One postal address, stored as JSON in `contact.addresses`. */
@Serializable
data class PostalAddress(
    val label: String = "",
    val street: String = "",
    val locality: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
) {
    fun isBlank() = listOf(street, locality, region, postcode, country).all { it.isBlank() }
    fun oneLine() = listOf(street, locality, region, postcode, country).filter { it.isNotBlank() }.joinToString(", ")
}

/** vCard N, stored as JSON in `contact.name_json`. */
@Serializable
data class StructuredName(
    val family: String = "",
    val given: String = "",
    val additional: String = "",
    val prefix: String = "",
    val suffix: String = "",
)

/** A birthday; [year] null when it isn't known. Feb 29 is valid. */
data class Birthday(val month: Int, val day: Int, val year: Int? = null) {
    init {
        require(isValid(month, day, year)) { "not a date: $year-$month-$day" }
    }

    companion object {
        private val DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /** True for a real calendar day. Without a year, Feb 29 counts (it is someone's birthday). */
        fun isValid(month: Int, day: Int, year: Int?): Boolean {
            if (month !in 1..12 || day < 1 || day > DAYS[month - 1]) return false
            if (year != null && (year < 1 || year > 9999)) return false
            if (year != null && month == 2 && day == 29 && !isLeap(year)) return false
            return true
        }

        fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

        fun of(month: Int?, day: Int?, year: Int?): Birthday? =
            if (month != null && day != null && isValid(month, day, year)) Birthday(month, day, year) else null
    }
}

/**
 * What the editor, capture and VCF import hand the repository. Fields Klean only carries (anniversary, extra
 * vCard properties, the photo) are kept by the repository on update.
 */
data class ContactDraft(
    val displayName: String,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val org: String? = null,
    val notes: String? = null,
    val birthday: Birthday? = null,
    /** Days before the birthday to remind; empty = no reminders. Null = the default from settings. */
    val reminderDays: List<Int>? = null,
    val name: StructuredName? = null,
    val anniversary: String? = null,
    /** JSON object of unmodelled vCard properties. Null keeps what the contact has. */
    val custom: String? = null,
    val uid: String? = null,
)

/** JSON helpers for the contact columns. Unreadable JSON reads as empty instead of failing the screen. */
object ContactJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val values = ListSerializer(LabeledValue.serializer())
    private val addresses = ListSerializer(PostalAddress.serializer())

    fun values(raw: String?): List<LabeledValue> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(values, raw) }.getOrDefault(emptyList())
    fun values(list: List<LabeledValue>): String = json.encodeToString(values, list)
    fun addresses(raw: String?): List<PostalAddress> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(addresses, raw) }.getOrDefault(emptyList())
    fun addresses(list: List<PostalAddress>): String = json.encodeToString(addresses, list)
    fun name(raw: String?): StructuredName? =
        raw?.let { runCatching { json.decodeFromString(StructuredName.serializer(), it) }.getOrNull() }
    fun name(n: StructuredName?): String? = n?.let { json.encodeToString(StructuredName.serializer(), it) }
}

/** `contact.birthday_reminders`: comma-separated days before, e.g. "0,3"; "" = off. */
object ReminderDays {
    val DEFAULT = listOf(0, 3)
    /** What the editor offers: on the day, 1, 3 and 7 days before. */
    val CHOICES = listOf(0, 1, 3, 7)

    fun decode(raw: String?): List<Int> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..60 }.distinct().sorted()
    fun encode(days: List<Int>): String = days.filter { it in 0..60 }.distinct().sorted().joinToString(",")
}

val ContactEntity.birthday: Birthday? get() = Birthday.of(birthdayMonth, birthdayDay, birthdayYear)
val ContactEntity.phoneList: List<LabeledValue> get() = ContactJson.values(phones)
val ContactEntity.emailList: List<LabeledValue> get() = ContactJson.values(emails)
val ContactEntity.addressList: List<PostalAddress> get() = ContactJson.addresses(addresses)
val ContactEntity.reminderDays: List<Int> get() = ReminderDays.decode(birthdayReminders)

/** The entity as a draft (for the editor and for merges). */
fun ContactEntity.toDraft() = ContactDraft(
    displayName = displayName, phones = phoneList, emails = emailList, addresses = addressList, org = org, notes = notes,
    birthday = birthday, reminderDays = reminderDays, name = ContactJson.name(nameJson), anniversary = anniversary,
    custom = custom, uid = remoteUid,
)
