package app.klean.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A Klean contact (Contacts + Birthdays modules), mirrored to the system Contacts provider. */
@Entity(tableName = "contact", indices = [Index("change_seq"), Index("birthday_month", "birthday_day")])
data class ContactEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** JSON array of {"label":"mobile","value":"+971…"}. */
    val phones: String = "[]",
    /** JSON array of {"label":"home","value":"a@b.c"}. */
    val emails: String = "[]",
    val notes: String? = null,
    @ColumnInfo(name = "birthday_month") val birthdayMonth: Int? = null,
    @ColumnInfo(name = "birthday_day") val birthdayDay: Int? = null,
    @ColumnInfo(name = "birthday_year") val birthdayYear: Int? = null,
    /** Comma-separated days before the birthday to remind, e.g. "0,3". */
    @ColumnInfo(name = "birthday_reminders") val birthdayReminders: String = "0,3",
    /** RawContacts._ID of the mirror, device-local. */
    @ColumnInfo(name = "provider_raw_id") val providerRawId: Long? = null,
    // v3 (M17): vCard 4 fields
    /** vCard N as JSON: {"family":"","given":"","additional":"","prefix":"","suffix":""}; null = only [displayName]. */
    @ColumnInfo(name = "name_json") val nameJson: String? = null,
    /** JSON array of {"label":"home","street":"","locality":"","region":"","postcode":"","country":""}. */
    @ColumnInfo(defaultValue = "[]") val addresses: String = "[]",
    val org: String? = null,
    /** Blob-store key of the contact photo (file name), synced as a blob; null = none. */
    @ColumnInfo(name = "photo_blob") val photoBlob: String? = null,
    /** `--MM-DD` (no year) or `yyyy-MM-dd`. */
    val anniversary: String? = null,
    /** JSON object of other vCard properties kept for round-trips (X-*, URL, NICKNAME, …). */
    @ColumnInfo(defaultValue = "{}") val custom: String = "{}",
    /** KLEAN, or `<account type>:<account name>` for a provider account (e.g. DAVx5). */
    @ColumnInfo(name = "source_account", defaultValue = "KLEAN") val sourceAccount: String = "KLEAN",
    /** vCard UID / remote id in the source account. */
    @ColumnInfo(name = "remote_uid") val remoteUid: String? = null,
    @Embedded val sync: SyncColumns,
)
