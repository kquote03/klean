package app.klean.backend.push

import app.klean.backend.ApiException
import app.klean.backend.KleanConfig
import app.klean.backend.KleanJson
import app.klean.backend.b64Url
import app.klean.backend.db.KleanDb
import app.klean.backend.db.PushSubscriptionsTable
import app.klean.backend.db.selectSetting
import app.klean.backend.db.upsertSetting
import app.klean.backend.device
import app.klean.backend.fromB64Url
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.security.interfaces.ECPublicKey
import kotlin.time.Clock

@Serializable data class PushKeys(val p256dh: String, val auth: String)
/** The browser's `PushSubscription.toJSON()` shape. */
@Serializable data class PushSubscribeRequest(val endpoint: String, val keys: PushKeys)
@Serializable data class PushUnsubscribeRequest(val endpoint: String)
@Serializable data class VapidKeyResponse(val publicKey: String)
@Serializable data class PushTestResponse(val sent: Int, val failed: Int)
@Serializable data class PushMessage(val title: String, val body: String, val tag: String? = null, val url: String? = null)

/** VAPID keys (generated once into `settings`), subscriptions per device, and delivery. */
class PushService(
    private val db: KleanDb,
    private val config: KleanConfig,
    private val http: HttpClient,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(PushService::class.java)

    private data class Vapid(val privateKey: java.security.PrivateKey, val publicRaw: ByteArray)

    private val vapid: Vapid by lazy { loadOrCreateVapid() }

    fun ensureKeys() { vapid }

    fun vapidPublicKey(): String = vapid.publicRaw.b64Url()

    private fun loadOrCreateVapid(): Vapid = db.blocking {
        val priv = selectSetting(KEY_PRIVATE)
        val pub = selectSetting(KEY_PUBLIC)
        if (priv != null && pub != null) {
            Vapid(WebPushCrypto.decodePrivate(priv.fromB64Url()), pub.fromB64Url())
        } else {
            val kp = WebPushCrypto.generateKeyPair()
            val raw = WebPushCrypto.encodePublic(kp.public as ECPublicKey)
            upsertSetting(KEY_PRIVATE, kp.private.encoded.b64Url())
            upsertSetting(KEY_PUBLIC, raw.b64Url())
            Vapid(kp.private, raw)
        }
    }

    suspend fun subscribe(deviceId: String, req: PushSubscribeRequest) {
        val url = runCatching { URLBuilder(req.endpoint).build() }.getOrNull()
        if (url == null || url.protocol.name != "https" || req.endpoint.length > 1000) {
            throw ApiException(HttpStatusCode.BadRequest, "endpoint must be an https URL")
        }
        val p256dh = runCatching { req.keys.p256dh.fromB64Url() }.getOrNull()
        val auth = runCatching { req.keys.auth.fromB64Url() }.getOrNull()
        if (p256dh == null || runCatching { WebPushCrypto.decodePublic(p256dh) }.isFailure || auth == null || auth.size != 16) {
            throw ApiException(HttpStatusCode.BadRequest, "bad subscription keys")
        }
        db.write {
            PushSubscriptionsTable.upsert {
                it[endpoint] = req.endpoint
                it[PushSubscriptionsTable.deviceId] = deviceId
                it[PushSubscriptionsTable.p256dh] = req.keys.p256dh
                it[PushSubscriptionsTable.auth] = req.keys.auth
                it[createdAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    suspend fun unsubscribe(deviceId: String, endpoint: String) {
        db.write {
            PushSubscriptionsTable.deleteWhere { (PushSubscriptionsTable.endpoint eq endpoint) and (PushSubscriptionsTable.deviceId eq deviceId) }
        }
    }

    /** Sends [message] to every subscription of [deviceId] (or all devices when null). Gone endpoints are removed. */
    suspend fun send(message: PushMessage, deviceId: String? = null, ttlSeconds: Int = 86_400): PushTestResponse {
        val subs = db.read {
            PushSubscriptionsTable.selectAll()
                .let { q -> if (deviceId == null) q else q.where { PushSubscriptionsTable.deviceId eq deviceId } }
                .map { Triple(it[PushSubscriptionsTable.endpoint], it[PushSubscriptionsTable.p256dh], it[PushSubscriptionsTable.auth]) }
        }
        var sent = 0
        var failed = 0
        val payload = KleanJson.encodeToString(PushMessage.serializer(), message).toByteArray()
        for ((endpoint, p256dh, auth) in subs) {
            val ok = runCatching {
                val body = WebPushCrypto.encrypt(payload, p256dh.fromB64Url(), auth.fromB64Url())
                val u = URLBuilder(endpoint).build()
                val audience = "${u.protocol.name}://${u.host}" + (if (u.port != u.protocol.defaultPort) ":${u.port}" else "")
                val exp = clock.now().epochSeconds + 12 * 3600
                val response = http.post(endpoint) {
                    header("TTL", ttlSeconds.toString())
                    header("Content-Encoding", "aes128gcm")
                    header("Urgency", "normal")
                    header("Authorization", WebPushCrypto.vapidHeader(audience, config.vapidSubject, exp, vapid.privateKey, vapid.publicRaw))
                    setBody(ByteArrayContent(body, ContentType.Application.OctetStream))
                }
                when {
                    response.status.value in 200..299 -> true
                    response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.Gone -> {
                        db.write { PushSubscriptionsTable.deleteWhere { PushSubscriptionsTable.endpoint eq endpoint } }
                        log.info("push subscription gone, removed")
                        false
                    }
                    else -> { log.warn("push failed status={}", response.status.value); false }
                }
            }.getOrElse { log.warn("push failed: {}", it.toString()); false }
            if (ok) sent++ else failed++
        }
        return PushTestResponse(sent, failed)
    }

    companion object {
        const val KEY_PRIVATE = "vapid.privateKey"
        const val KEY_PUBLIC = "vapid.publicKey"
    }
}

fun Route.pushRoutes(push: PushService) {
    get("/push/vapid-public-key") {
        call.device
        call.respond(VapidKeyResponse(push.vapidPublicKey()))
    }
    post("/push/subscribe") {
        push.subscribe(call.device.id, call.receive<PushSubscribeRequest>())
        call.respond(HttpStatusCode.Created, JsonObject(mapOf("ok" to JsonPrimitive(true))))
    }
    post("/push/unsubscribe") {
        push.unsubscribe(call.device.id, call.receive<PushUnsubscribeRequest>().endpoint)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/push/test") {
        val me = call.device
        call.respond(push.send(PushMessage("Klean", "Notifications from Klean reach this browser.", tag = "klean-test"), me.id, ttlSeconds = 600))
    }
}
