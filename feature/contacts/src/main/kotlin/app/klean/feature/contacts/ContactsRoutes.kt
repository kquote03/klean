package app.klean.feature.contacts

import kotlinx.serialization.Serializable

@Serializable object ContactsGraph
/** The Contacts screen; [tab] "birthdays" opens the Birthdays tab. */
@Serializable data class ContactsRoute(val tab: String? = null)
/** A Klean contact ([contactId]) or a DAVx5 one ([rawId] ≥ 0). */
@Serializable data class ContactDetailRoute(val contactId: String? = null, val rawId: Long = -1L)
/** No id adds a contact. */
@Serializable data class ContactEditRoute(val contactId: String? = null, val rawId: Long = -1L)
@Serializable object ContactsSettingsRoute
@Serializable object BirthdaysSettingsRoute

object ContactsLinks {
    const val CONTACTS = "klean://contacts"
    const val BIRTHDAYS = "klean://contacts/birthdays"
    const val CONTACT = "klean://contacts/contact/{contactId}"
    fun contact(id: String) = "klean://contacts/contact/$id"
    const val TAB_BIRTHDAYS = "birthdays"
}
