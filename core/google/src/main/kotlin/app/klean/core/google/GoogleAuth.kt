package app.klean.core.google

import android.content.Intent
import android.content.IntentSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The OAuth scope Klean asks for: read and edit Google Tasks. Calendar is never requested here. */
const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"

/** One step of connecting a Google account. */
sealed interface AuthStep {
    data object Granted : AuthStep
    /** Google wants the user to pick an account or agree: launch [intentSender], then call [GoogleAuth.finishConsent]. */
    data class NeedsConsent(val intentSender: IntentSender) : AuthStep
    /** The user backed out of the consent screen. Nothing changes. */
    data object Declined : AuthStep
    /** No Google Play services on this phone (Waydroid, de-Googled phones), or the OAuth client isn't set up. */
    data class Unavailable(val reason: String) : AuthStep
}

/**
 * Google sign-in for Tasks. Access tokens stay in memory; Klean keeps only a "connected" flag on the device.
 * The production binding is [PlayGoogleAuth] (Play services `AuthorizationClient`); tests use [FakeGoogleAuth].
 */
interface GoogleAuth {
    /** True once the user connected Google Tasks on this phone (until Disconnect). */
    val connected: Flow<Boolean>
    /** False on phones without Google Play services; the settings row then explains why. */
    fun available(): Boolean
    /** Asks for [TASKS_SCOPE]. May need the user's consent first. */
    suspend fun connect(): AuthStep
    /** The result of the consent screen [AuthStep.NeedsConsent] opened. */
    suspend fun finishConsent(data: Intent?): AuthStep
    /** A current access token without any UI, or null (not connected, consent needed again, no network). */
    suspend fun accessToken(): String?
    /** The server said 401 for [token]: forget it, so the next [accessToken] fetches a fresh one. */
    suspend fun invalidate(token: String)
    suspend fun disconnect()
}

/** In-memory [GoogleAuth] for tests and previews. */
class FakeGoogleAuth(
    var token: String? = "fake-token",
    var isAvailable: Boolean = true,
    var consentNeeded: IntentSender? = null,
    connected: Boolean = false,
) : GoogleAuth {
    private val state = MutableStateFlow(connected)
    override val connected: Flow<Boolean> = state.asStateFlow()
    val invalidated = mutableListOf<String>()
    /** Tokens handed out after an invalidation (token rotation in tests). */
    val nextTokens = ArrayDeque<String>()

    override fun available(): Boolean = isAvailable

    override suspend fun connect(): AuthStep {
        if (!isAvailable) return AuthStep.Unavailable("no play services")
        consentNeeded?.let { return AuthStep.NeedsConsent(it) }
        state.value = true
        return AuthStep.Granted
    }

    override suspend fun finishConsent(data: Intent?): AuthStep {
        if (data == null) return AuthStep.Declined
        state.value = true
        return AuthStep.Granted
    }

    override suspend fun accessToken(): String? = if (state.value) token else null

    override suspend fun invalidate(token: String) {
        invalidated += token
        this.token = nextTokens.removeFirstOrNull()
    }

    override suspend fun disconnect() {
        state.value = false
    }
}
