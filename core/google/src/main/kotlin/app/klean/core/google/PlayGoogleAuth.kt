package app.klean.core.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google sign-in through Play services' `AuthorizationClient` (no client id in the app: Google matches the
 * Android OAuth client by package name and signing certificate, see docs/google-oauth.md). Access tokens are
 * kept in memory only; a silent `authorize` call fetches a new one when it expires.
 */
@Singleton
class PlayGoogleAuth @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val state: GoogleLocalState,
    private val clock: Clock,
) : GoogleAuth {
    override val connected: Flow<Boolean> = state.connected

    @Volatile private var token: String? = null
    @Volatile private var tokenAt: Long = 0
    /** Whether [token] was granted with [PROFILE_SCOPES] too. */
    @Volatile private var tokenHasProfile = false

    /**
     * [withProfile]: Tasks plus the profile scopes (connecting, the daily profile refresh). Silent token refreshes
     * ask for Tasks only, so an install connected before the profile scopes keeps syncing without a consent screen.
     */
    private fun request(account: String?, withProfile: Boolean, pickAccount: Boolean = false): AuthorizationRequest {
        val scopes = listOf(Scope(TASKS_SCOPE)) + if (withProfile) PROFILE_SCOPES.map(::Scope) else emptyList()
        return AuthorizationRequest.builder().setRequestedScopes(scopes).apply {
            account?.let { setAccount(Account(it, ACCOUNT_TYPE)) }
            if (pickAccount) setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }.build()
    }

    override fun available(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    override suspend fun connect(account: String?, pickAccount: Boolean): AuthStep {
        if (!available()) return AuthStep.Unavailable("Google Play services missing")
        return try {
            accept(Identity.getAuthorizationClient(ctx).authorize(request(account, withProfile = true, pickAccount = pickAccount && account == null)).await())
        } catch (e: Exception) {
            Log.w(TAG, "authorize failed", e)
            AuthStep.Unavailable(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun finishConsent(data: Intent?): AuthStep {
        if (data == null) return AuthStep.Declined
        return try {
            accept(Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            AuthStep.Declined
        }
    }

    private suspend fun accept(r: AuthorizationResult): AuthStep {
        if (r.hasResolution()) return r.pendingIntent?.let { AuthStep.NeedsConsent(it.intentSender) } ?: AuthStep.Declined
        val t = r.accessToken ?: return AuthStep.Declined
        // Connecting always asks for the profile scopes; Google reports them as "profile" or ".../userinfo.profile".
        remember(t, withProfile = r.grantedScopes.any { it.contains("profile") })
        state.setConnected(true)
        return AuthStep.Granted
    }

    override suspend fun accessToken(): String? {
        if (!state.connected.first()) return null
        token?.takeIf { clock.now().toEpochMilliseconds() - tokenAt < TOKEN_TTL_MS }?.let { return it }
        if (!available()) return null
        return silent(withProfile = false)
    }

    override suspend fun profileToken(): String? {
        if (!state.connected.first()) return null
        token?.takeIf { tokenHasProfile && clock.now().toEpochMilliseconds() - tokenAt < TOKEN_TTL_MS }?.let { return it }
        if (!available()) return null
        return silent(withProfile = true)
    }

    /** A token for the current account without UI; null when Google wants consent first. */
    private suspend fun silent(withProfile: Boolean): String? = try {
        val r = Identity.getAuthorizationClient(ctx).authorize(request(state.currentEmail(), withProfile)).await()
        if (r.hasResolution()) null else r.accessToken?.also { remember(it, withProfile) }
    } catch (e: Exception) {
        Log.w(TAG, "silent authorize failed", e)
        null
    }

    private fun remember(t: String, withProfile: Boolean) {
        token = t
        tokenHasProfile = withProfile
        tokenAt = clock.now().toEpochMilliseconds()
    }

    override suspend fun invalidate(token: String) {
        if (this.token == token) this.token = null
    }

    override suspend fun disconnect() {
        token = null
        state.setConnected(false)
    }

    private companion object {
        const val TAG = "KleanGoogle"
        const val ACCOUNT_TYPE = "com.google"
        /** Google access tokens last an hour; refresh a little early. */
        const val TOKEN_TTL_MS = 50 * 60_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
