package app.klean.core.data.account

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/**
 * The sync adapter for both Calendar and Contacts (see `sync_calendar.xml`, `sync_contacts.xml`). It does
 * nothing: Klean writes provider rows itself, as the account's sync adapter, whenever its own data changes.
 * Declaring it is what lets Klean own rows under the Klean account.
 */
open class KleanSyncService : Service() {
    private val adapter by lazy { NoOpSyncAdapter(this) }

    override fun onBind(intent: Intent?): IBinder? = adapter.syncAdapterBinder

    private class NoOpSyncAdapter(service: Service) : AbstractThreadedSyncAdapter(service, true) {
        override fun onPerformSync(
            account: Account?, extras: Bundle?, authority: String?, provider: ContentProviderClient?, syncResult: SyncResult?,
        ) = Unit
    }
}

/** The same no-op adapter, declared a second time for the contacts authority (a service declares one adapter). */
class KleanContactsSyncService : KleanSyncService()
