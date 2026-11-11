package app.klean.core.google

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What Today's avatar and the account menu show. */
data class AccountsState(
    val available: Boolean = true,
    val connected: Boolean = false,
    /** The account whose Google Tasks lists show; null while connected means its profile isn't known yet. */
    val current: GoogleAccount? = null,
    /** Every account Klean was authorized with on this phone, in the order they were added. */
    val accounts: List<GoogleAccount> = emptyList(),
    /** Each account's downloaded photo, by email (only files that exist). */
    val photos: Map<String, File> = emptyMap(),
) {
    /** Connected before Klean asked for the profile scopes: the menu offers "Show your Google photo". */
    val needsProfile: Boolean get() = connected && current == null

    companion object {
        val None = AccountsState()
    }
}

/** How [GoogleAccounts.adoptGranted] went. */
sealed interface AdoptResult {
    /** [email] is now current; [switched] when another account was current before. */
    data class Identified(val email: String, val switched: Boolean) : AdoptResult
    /** The grant has no profile scopes (an install connected before them). Nothing changes. */
    data object NoProfile : AdoptResult
    /** Couldn't read the profile (offline). Nothing changes; the next refresh tries again. */
    data object Failed : AdoptResult
}

/** Today's view of the accounts: what the avatar shows, and the swipe-to-switch gesture. */
interface AccountSource {
    val state: Flow<AccountsState>

    /**
     * Switches to the next ([step] 1) or previous ([step] -1) account without any UI, then syncs. False when there is
     * nothing to switch to or Google wants the user's consent first (the caller opens the account menu).
     */
    suspend fun switchBy(step: Int): Boolean
}

/** No Google (tests, previews): a neutral avatar and nothing to switch. */
object NoAccounts : AccountSource {
    override val state: Flow<AccountsState> = kotlinx.coroutines.flow.flowOf(AccountsState.None)
    override suspend fun switchBy(step: Int) = false
}

/**
 * The Google accounts Klean was authorized with on this phone (Google's account-switcher pattern). After every
 * grant it reads the OpenID profile (email, name, photo), so the menu can say which account is connected. One
 * account is current: its Google Tasks lists show and only its queued edits are sent. Profile data stays on the
 * phone: DataStore `google` and [photoDir] (app-private), never synced and never in backups.
 */
class GoogleAccounts(
    private val auth: GoogleAuth,
    private val profile: GoogleProfileApi,
    private val local: GoogleLocalState,
    private val sync: GoogleTasksSync,
    private val photoDir: File,
    private val clock: Clock,
) : AccountSource {
    @Volatile private var lastAttemptAt = 0L

    override val state: Flow<AccountsState> = combine(auth.connected, local.accounts, local.currentEmail) { connected, all, current ->
        AccountsState(
            available = auth.available(),
            connected = connected,
            current = if (connected) all.firstOrNull { it.email == current } else null,
            accounts = if (connected) all else emptyList(),
            photos = all.mapNotNull { a -> photoFile(a)?.let { a.email to it } }.toMap(),
        )
    }

    private fun photoFile(a: GoogleAccount): File? = a.pictureFile?.let { File(photoDir, it) }?.takeIf { it.isFile }

    /**
     * After any granted step (connect, switch, consent): learns which account the grant is for and makes it current.
     * When another account was current, that one's lists and tasks leave Klean first and its unsent edits stay
     * queued under its own email. Then the photo is fetched. The caller syncs afterwards.
     */
    suspend fun adoptGranted(): AdoptResult {
        lastAttemptAt = clock.now().toEpochMilliseconds()
        val token = auth.profileToken() ?: return AdoptResult.NoProfile
        val info = try {
            profile.userInfo(token)
        } catch (e: IOException) {
            // Whose token this is isn't known: drop it, so the next request asks for the current account again.
            if (local.currentEmail() != null) auth.invalidate(token)
            return if (e is GoogleHttpException && (e.status == 401 || e.status == 403)) AdoptResult.NoProfile else AdoptResult.Failed
        }
        val email = info.email ?: return AdoptResult.Failed
        val now = clock.now().toEpochMilliseconds()
        var switched = false
        val account = sync.exclusive {
            val current = local.currentEmail()
            if (current != null && current != email) {
                sync.parkCurrentAccount()
                switched = true
            }
            if (current == null) local.claimUntaggedOutbox(email)
            val prev = local.accounts().firstOrNull { it.email == email }
            val next = GoogleAccount(
                email = email, name = info.name, givenName = info.givenName, pictureUrl = info.picture,
                pictureFile = prev?.pictureFile?.takeIf { prev.pictureUrl == info.picture }, profileAt = now,
            )
            local.upsertAccount(next)
            local.setCurrent(email)
            local.setConnected(true)
            next
        }
        downloadPhoto(account)
        return AdoptResult.Identified(email, switched)
    }

    /** At most daily (each sync calls this): refreshes the current account's profile and photo. */
    suspend fun refreshIfStale() {
        if (!auth.connected.first()) return
        val now = clock.now().toEpochMilliseconds()
        if (now - lastAttemptAt < DAY_MS) return
        val current = local.currentEmail()?.let { e -> local.accounts().firstOrNull { it.email == e } }
        if (current != null && now - current.profileAt < DAY_MS && (current.pictureUrl == null || photoFile(current) != null)) return
        adoptGranted()
    }

    /**
     * Makes [email] current: asks Google for it (silently when it was granted before). On [AuthStep.Granted] the
     * caller runs [adoptGranted] and a sync; [AuthStep.NeedsConsent] goes through the consent screen first.
     */
    suspend fun switchTo(email: String): AuthStep = auth.connect(account = email)

    override suspend fun switchBy(step: Int): Boolean {
        val all = local.accounts()
        val target = neighbour(all, local.currentEmail(), step) ?: return false
        if (auth.connect(account = target.email) != AuthStep.Granted) return false
        if (adoptGranted() !is AdoptResult.Identified) return false
        sync.run()
        return true
    }

    /** Disconnect: every account's entry and photo leave the phone. */
    suspend fun forgetAll() {
        local.clearAccounts()
        withContext(Dispatchers.IO) { photoDir.listFiles()?.forEach { it.delete() } }
    }

    private suspend fun downloadPhoto(a: GoogleAccount) {
        val url = a.pictureUrl ?: return removePhoto(a)
        if (a.pictureFile != null && photoFile(a) != null) return
        val bytes = try {
            profile.picture(pictureAtSize(url))
        } catch (e: IOException) {
            return
        }
        if (!looksLikeImage(bytes)) return
        val name = photoName(a.email)
        withContext(Dispatchers.IO) {
            photoDir.mkdirs()
            val tmp = File(photoDir, "$name.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(File(photoDir, name))) {
                tmp.copyTo(File(photoDir, name), overwrite = true)
                tmp.delete()
            }
        }
        local.upsertAccount(a.copy(pictureFile = name))
    }

    private suspend fun removePhoto(a: GoogleAccount) {
        val f = photoFile(a) ?: return
        withContext(Dispatchers.IO) { f.delete() }
        local.upsertAccount(a.copy(pictureFile = null))
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60_000L

        /** The app-private folder for account photos (outside the backed-up `photos` folder). */
        const val PHOTO_DIR = "google-accounts"

        /** The photo's file name: a hash of the email, so the folder never lists addresses. */
        fun photoName(email: String): String =
            MessageDigest.getInstance("SHA-256").digest(email.toByteArray()).take(12).joinToString("") { "%02x".format(it) } + ".img"

        /** The account after ([step] 1) or before ([step] -1) [current], wrapping around; null with fewer than two. */
        fun neighbour(accounts: List<GoogleAccount>, current: String?, step: Int): GoogleAccount? {
            if (accounts.size < 2 || step == 0) return null
            val i = accounts.indexOfFirst { it.email == current }
            if (i < 0) return accounts.first()
            return accounts[Math.floorMod(i + step, accounts.size)]
        }
    }
}
