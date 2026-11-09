package app.klean.core.data.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Klean's own Android account ("Klean"). Calendar and Contacts write their provider rows under it as a
 * sync adapter, so the system calendar and dialer show Klean's events and contacts and the rows are
 * clearly Klean's. There is no login and nothing leaves the phone; the authenticator and sync adapters
 * are stubs ([KleanAuthenticatorService], [KleanSyncService]).
 *
 * The type is the application id (`app.klean`, `app.klean.debug`), matching the `klean_account_type`
 * string the authenticator XML declares (the debug build overrides it).
 */
object KleanAccount {
    const val NAME = "Klean"
    const val CALENDAR_AUTHORITY = "com.android.calendar"
    const val CONTACTS_AUTHORITY = "com.android.contacts"

    fun accountType(ctx: Context): String = ctx.packageName

    fun account(ctx: Context): Account = Account(NAME, accountType(ctx))

    /** Adds the account if it is missing and marks it syncable for Calendar and Contacts. Idempotent. */
    suspend fun ensure(ctx: Context): Account = withContext(Dispatchers.IO) {
        val account = account(ctx)
        val manager = AccountManager.get(ctx)
        if (manager.getAccountsByType(account.type).none { it.name == account.name }) {
            manager.addAccountExplicitly(account, null, null)
        }
        listOf(CALENDAR_AUTHORITY, CONTACTS_AUTHORITY).forEach { ContentResolver.setIsSyncable(account, it, 1) }
        account
    }
}
