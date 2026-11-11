package app.klean.core.google

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** The production [AccountActions]: Google sign-in, the account list, the hourly sync schedule. */
@Singleton
class GoogleAccountActions @Inject constructor(
    private val auth: GoogleAuth,
    private val accounts: GoogleAccounts,
    private val sync: GoogleTasksSync,
    private val scheduler: GoogleTasksScheduler,
) : AccountActions {
    override val state: Flow<AccountsState> = accounts.state
    override suspend fun switchBy(step: Int): Boolean = accounts.switchBy(step)
    override suspend fun connect(pick: Boolean): AuthStep = auth.connect(pickAccount = pick)
    override suspend fun switchTo(email: String): AuthStep = accounts.switchTo(email)
    override suspend fun finishConsent(data: Intent?): AuthStep = auth.finishConsent(data)

    override suspend fun completeGrant(): AdoptResult {
        scheduler.schedulePeriodic()
        val r = accounts.adoptGranted()
        sync.run()
        return r
    }
}
