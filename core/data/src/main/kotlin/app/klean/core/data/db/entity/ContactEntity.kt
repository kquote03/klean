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
    @Embedded val sync: SyncColumns,
)
