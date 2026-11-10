package app.klean.feature.contacts

import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.device.DeviceContact
import app.klean.feature.contacts.device.DeviceContacts

class FakeDeviceContacts(var granted: Boolean = true) : DeviceContacts {
    val list = mutableListOf<DeviceContact>()
    var accounts = listOf("me@dav")
    val updates = mutableListOf<Pair<Long, ContactDraft>>()
    override fun canRead() = granted
    override fun canWrite() = granted
    override suspend fun davAccounts() = if (granted) accounts else emptyList()
    override suspend fun contacts() = if (granted) list.toList() else emptyList()
    override suspend fun get(rawId: Long) = list.firstOrNull { it.rawId == rawId }
    override suspend fun update(rawId: Long, draft: ContactDraft) {
        updates += rawId to draft
        val i = list.indexOfFirst { it.rawId == rawId }
        if (i >= 0) list[i] = list[i].copy(displayName = draft.displayName, phones = draft.phones, emails = draft.emails, birthday = draft.birthday)
    }
}
