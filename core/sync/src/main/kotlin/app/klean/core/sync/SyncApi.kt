package app.klean.core.sync

import app.klean.shared.sync.PullResponse
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.PushResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The backend's sync and blob routes (docs/backend-api.md), for one paired account. */
interface SyncApi {
    suspend fun push(request: PushRequest): PushResponse
    suspend fun pull(cursor: Long, limit: Int): PullResponse
    /** `HEAD /blobs/{sha}`: true when the server already has it. */
    suspend fun hasBlob(sha256: String): Boolean
    suspend fun putBlob(sha256: String, bytes: ByteArray, contentType: String)
    /** The bytes, or null when the server doesn't have them. */
    suspend fun getBlob(sha256: String): ByteArray?
}

/** A non-success answer. 401/403 mean the token no longer works (unpaired elsewhere). */
class SyncHttpException(val status: Int, message: String) : IOException("HTTP $status: $message") {
    val unauthorized get() = status == 401 || status == 403
}

val SyncJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

@Serializable data class PairClaimBody(val code: String, val deviceName: String, val platform: String = "ANDROID", val deviceId: String? = null)
@Serializable data class PairClaimResult(val deviceId: String, val token: String)

/** `baseUrl` is the backend's public URL (the pairing link's `url`), without `/api/v1`. */
class KtorSyncApi(private val client: HttpClient, baseUrl: String, private val token: String) : SyncApi {
    private val api = baseUrl.trimEnd('/') + "/api/v1"

    override suspend fun push(request: PushRequest): PushResponse {
        val r = client.post("$api/sync/push") {
            bearerAuth(token)
            setBody(TextContent(SyncJson.encodeToString(PushRequest.serializer(), request), ContentType.Application.Json))
        }
        return SyncJson.decodeFromString(PushResponse.serializer(), r.ok().bodyAsText())
    }

    override suspend fun pull(cursor: Long, limit: Int): PullResponse {
        val r = client.get("$api/sync/pull") {
            bearerAuth(token)
            parameter("cursor", cursor)
            parameter("limit", limit)
        }
        return SyncJson.decodeFromString(PullResponse.serializer(), r.ok().bodyAsText())
    }

    override suspend fun hasBlob(sha256: String): Boolean {
        val r = client.head("$api/blobs/$sha256") { bearerAuth(token) }
        if (r.status == HttpStatusCode.NotFound) return false
        r.ok()
        return true
    }

    override suspend fun putBlob(sha256: String, bytes: ByteArray, contentType: String) {
        client.put("$api/blobs/$sha256") {
            bearerAuth(token)
            setBody(ByteArrayContent(bytes, ContentType.parse(contentType)))
        }.ok()
    }

    override suspend fun getBlob(sha256: String): ByteArray? {
        val r = client.get("$api/blobs/$sha256") { bearerAuth(token) }
        if (r.status == HttpStatusCode.NotFound) return null
        return r.ok().bodyAsBytes()
    }

    /** Revokes this device's token (`DELETE /devices/{id}`); best effort on unpair. */
    suspend fun revoke(deviceId: String) {
        client.delete("$api/devices/$deviceId") { bearerAuth(token) }
    }
}

/** `POST /pair/claim`. Sends this phone's `device_id`; if the server already knows it (an earlier pairing), claims without it. */
suspend fun claimPairing(client: HttpClient, baseUrl: String, body: PairClaimBody): PairClaimResult {
    val api = baseUrl.trimEnd('/') + "/api/v1"
    suspend fun send(b: PairClaimBody) = client.post("$api/pair/claim") {
        setBody(TextContent(SyncJson.encodeToString(PairClaimBody.serializer(), b), ContentType.Application.Json))
    }
    var r = send(body)
    // 409 = deviceId already paired once (revoked ids stay taken). The code isn't used up by that check.
    if (r.status == HttpStatusCode.Conflict && body.deviceId != null) r = send(body.copy(deviceId = null))
    return SyncJson.decodeFromString(PairClaimResult.serializer(), r.ok().bodyAsText())
}

private suspend fun HttpResponse.ok(): HttpResponse {
    if (!status.isSuccess()) throw SyncHttpException(status.value, runCatching { bodyAsText() }.getOrDefault("").take(200))
    return this
}
