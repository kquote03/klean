package app.klean.core.data.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/** Lets the system own the "Klean" account. There are no credentials, so almost everything is unsupported. */
class KleanAuthenticatorService : Service() {
    private val authenticator by lazy { StubAuthenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder

    private class StubAuthenticator(ctx: Context) : AbstractAccountAuthenticator(ctx) {
        // Accounts are only added by the app itself (KleanAccount.ensure), never from system Settings.
        override fun addAccount(
            response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?,
            requiredFeatures: Array<out String>?, options: Bundle?,
        ): Bundle? = null

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?): Bundle =
            throw UnsupportedOperationException()

        override fun confirmCredentials(response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?): Bundle =
            throw UnsupportedOperationException()

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?,
        ): Bundle = throw UnsupportedOperationException()

        override fun getAuthTokenLabel(authTokenType: String?): String = throw UnsupportedOperationException()

        override fun updateCredentials(
            response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?,
        ): Bundle = throw UnsupportedOperationException()

        override fun hasFeatures(response: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?): Bundle =
            throw UnsupportedOperationException()
    }
}
