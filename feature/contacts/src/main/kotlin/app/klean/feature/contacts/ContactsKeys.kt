package app.klean.feature.contacts

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Contacts' and Birthdays' synced settings (kept in the module so parallel modules never collide). */
object ContactsKeys {
    /** Birthday reminders for new contacts: days before ("0,3" = on the day and 3 days before; "" = none). */
    val DefaultReminders = SettingKey("birthdays.defaultReminders", "0,3", String.serializer())
    /** Show the age a contact turns when the year is known. */
    val ShowAge = SettingKey("birthdays.showAge", true, Boolean.serializer())
    /** Local hour birthday reminders go off at. */
    val ReminderHour = SettingKey("birthdays.reminderHour", 9, Int.serializer())
    /** The one-time "These look like the same people" offer was answered or set aside. */
    val MergeOfferDone = SettingKey("contacts.mergeOfferDone", false, Boolean.serializer())
    /** Pairs the user chose to keep apart ("idA|idB", sorted). */
    val KeptApart = SettingKey("contacts.keptApart", emptyList<String>(), ListSerializer(String.serializer()))
}
