package app.klean.core.google

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The OpenID `userinfo` fields Klean reads. Nothing else of the profile is kept. */
@Serializable
data class GoogleUserInfo(
    val email: String? = null,
    val name: String? = null,
    @SerialName("given_name") val givenName: String? = null,
    val picture: String? = null,
)

private val UserInfoJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Parses a `userinfo` response; null when it isn't JSON or has no email (the `email` scope wasn't granted). */
fun parseUserInfo(body: String): GoogleUserInfo? {
    val info = runCatching { UserInfoJson.decodeFromString(GoogleUserInfo.serializer(), body) }.getOrNull() ?: return null
    val email = info.email?.trim()?.lowercase()?.takeIf { '@' in it } ?: return null
    return info.copy(
        email = email,
        name = info.name?.trim()?.takeIf { it.isNotEmpty() },
        givenName = info.givenName?.trim()?.takeIf { it.isNotEmpty() },
        picture = info.picture?.trim()?.takeIf { it.startsWith("https://") },
    )
}

/**
 * A Google profile photo URL at [px] pixels, square-cropped (Google's `=sNN-c` suffix). Replaces a size suffix
 * that is already there (`=s96-c`), or adds one.
 */
fun pictureAtSize(url: String, px: Int = PICTURE_PX): String {
    val suffix = Regex("""=[swh]\d+(-[a-z0-9]+)*$""")
    val base = if (suffix.containsMatchIn(url)) url.replace(suffix, "") else url
    return "$base=s$px-c"
}

/** The photo is stored once at this size (enough for the menu's large avatar on an xxxhdpi screen). */
const val PICTURE_PX = 192

/** Reads the account's profile and its photo. Fake in tests ([GoogleProfileClient] on a MockEngine). */
interface GoogleProfileApi {
    /** The profile behind [token]. Throws [GoogleHttpException] (401/403: no profile scopes) or an IOException. */
    suspend fun userInfo(token: String): GoogleUserInfo
    /** The photo's bytes. */
    suspend fun picture(url: String): ByteArray
}

class GoogleProfileClient(
    private val client: HttpClient,
    private val userInfoUrl: String = USERINFO_URL,
) : GoogleProfileApi {
    override suspend fun userInfo(token: String): GoogleUserInfo {
        val r = client.get(userInfoUrl) { bearerAuth(token) }
        if (!r.status.isSuccess()) throw GoogleHttpException(r.status.value, "userinfo")
        return parseUserInfo(r.bodyAsText()) ?: throw GoogleHttpException(403, "userinfo has no email")
    }

    override suspend fun picture(url: String): ByteArray {
        val r = client.get(url)
        if (!r.status.isSuccess()) throw GoogleHttpException(r.status.value, "picture")
        return r.bodyAsBytes()
    }

    companion object {
        const val USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo"
    }
}

/** JPEG, PNG, WebP or GIF, by magic bytes: anything else from the photo URL is not written to disk. */
fun looksLikeImage(b: ByteArray): Boolean {
    fun at(i: Int) = b.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
    return (at(0) == 0xFF && at(1) == 0xD8) ||
        (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) ||
        (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && at(8) == 0x57 && at(9) == 0x45) ||
        (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46)
}
