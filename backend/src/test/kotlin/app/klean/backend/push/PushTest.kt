package app.klean.backend.push

import app.klean.backend.Services
import app.klean.backend.b64Url
import app.klean.backend.claimDevice
import app.klean.backend.fromB64Url
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushTest {
    /** A browser-side subscription: its own P-256 key pair and auth secret. */
    private class Browser {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val auth = ByteArray(16) { (it * 7).toByte() }
        val p256dh = WebPushCrypto.encodePublic(keys.public as ECPublicKey)

        /** RFC 8291 §3 decryption, written from the receiver's side. */
        fun decrypt(body: ByteArray): ByteArray {
            val buf = ByteBuffer.wrap(body)
            val salt = ByteArray(16).also { buf.get(it) }
            buf.int // record size
            val idLen = buf.get().toInt()
            val asPublic = ByteArray(idLen).also { buf.get(it) }
            val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }
            val ka = KeyAgreement.getInstance("ECDH").apply {
                init(keys.private); doPhase(WebPushCrypto.decodePublic(asPublic), true)
            }
            val ecdh = ka.generateSecret()
            fun hmac(key: ByteArray, data: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
            val prkKey = hmac(auth, ecdh)
            val ikm = hmac(prkKey, "WebPush: info".toByteArray() + 0 + p256dh + asPublic + 1)
            val prk = hmac(salt, ikm)
            val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(16)
            val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(12)
            val plain = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
            }.doFinal(ciphertext)
            val end = plain.indexOfLast { it.toInt() == 2 }
            return plain.copyOf(end)
        }
    }

    private val captured = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.Created
    private val engine = MockEngine { req -> captured += req; respond("", status) }
    private fun services(): (app.klean.backend.KleanConfig) -> Services = { Services.create(it, httpClient = HttpClient(engine)) }

    private suspend fun bodyBytes(req: HttpRequestData): ByteArray = when (val b = req.body) {
        is OutgoingContent.ByteArrayContent -> b.bytes()
        else -> error("unexpected body ${b::class}")
    }

    @Test fun subscribeThenTestPushIsEncryptedAndVapidSigned() = kleanTest(services = services()) {
        val c = jsonClient()
        val web = c.login()
        val browser = Browser()
        val vapid = c.get("/api/v1/push/vapid-public-key") { bearerAuth(web.token) }.body<VapidKeyResponse>().publicKey

        val sub = c.post("/api/v1/push/subscribe") {
            bearerAuth(web.token); contentType(ContentType.Application.Json)
            setBody(PushSubscribeRequest("https://push.example.net/send/abc", PushKeys(browser.p256dh.b64Url(), browser.auth.b64Url())))
        }
        assertEquals(HttpStatusCode.Created, sub.status)

        val result = c.post("/api/v1/push/test") { bearerAuth(web.token) }.body<PushTestResponse>()
        assertEquals(PushTestResponse(sent = 1, failed = 0), result)

        val req = captured.single()
        assertEquals("https://push.example.net/send/abc", req.url.toString())
        assertEquals("aes128gcm", req.headers["Content-Encoding"])
        assertTrue(req.headers["TTL"]!!.toInt() > 0)

        // Payload decrypts with the browser's keys.
        val payload = Json.parseToJsonElement(browser.decrypt(bodyBytes(req)).decodeToString()).jsonObject
        assertEquals("Klean", payload["title"]!!.jsonPrimitive.content)

        // VAPID: `vapid t=<jwt>, k=<server public key>`, JWT signed ES256 by that key, aud = push origin.
        val auth = req.headers["Authorization"]!!
        val t = Regex("t=([^,\\s]+)").find(auth)!!.groupValues[1]
        val k = Regex("k=([^,\\s]+)").find(auth)!!.groupValues[1]
        assertEquals(vapid, k)
        val (h, p, s) = t.split('.')
        val claims = Json.parseToJsonElement(p.fromB64Url().decodeToString()).jsonObject
        assertEquals("https://push.example.net", claims["aud"]!!.jsonPrimitive.content)
        val ok = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initVerify(WebPushCrypto.decodePublic(k.fromB64Url())); update("$h.$p".toByteArray())
        }.verify(s.fromB64Url())
        assertTrue(ok)
    }

    @Test fun goneSubscriptionsAreDropped() = kleanTest(services = services()) {
        val c = jsonClient()
        val web = c.login()
        val browser = Browser()
        c.post("/api/v1/push/subscribe") {
            bearerAuth(web.token); contentType(ContentType.Application.Json)
            setBody(PushSubscribeRequest("https://push.example.net/send/gone", PushKeys(browser.p256dh.b64Url(), browser.auth.b64Url())))
        }
        status = HttpStatusCode.Gone
        assertEquals(PushTestResponse(0, 1), c.post("/api/v1/push/test") { bearerAuth(web.token) }.body<PushTestResponse>())
        status = HttpStatusCode.Created
        assertEquals(PushTestResponse(0, 0), c.post("/api/v1/push/test") { bearerAuth(web.token) }.body<PushTestResponse>())
    }

    @Test fun badSubscriptionsAreRejected() = kleanTest(services = services()) {
        val c = jsonClient()
        val phone = c.claimDevice(c.login().token, "Pixel")
        val browser = Browser()
        suspend fun sub(endpoint: String, p256dh: String) = c.post("/api/v1/push/subscribe") {
            bearerAuth(phone.token); contentType(ContentType.Application.Json)
            setBody(PushSubscribeRequest(endpoint, PushKeys(p256dh, browser.auth.b64Url())))
        }.status
        assertEquals(HttpStatusCode.BadRequest, sub("http://push.example.net/x", browser.p256dh.b64Url()))
        assertEquals(HttpStatusCode.BadRequest, sub("https://push.example.net/x", "AAAA"))
    }

    @Test fun vapidKeysPersistAcrossRestarts() {
        val config = app.klean.backend.testConfig()
        val first = Services.create(config, httpClient = HttpClient(engine)).use { it.push.vapidPublicKey() }
        val second = Services.create(config, httpClient = HttpClient(engine)).use { it.push.vapidPublicKey() }
        assertEquals(first, second)
        assertEquals(65, first.fromB64Url().size)
    }

    @Test fun encryptionUsesAFreshKeyAndSaltEachTime() {
        val browser = Browser()
        val a = WebPushCrypto.encrypt("hi".toByteArray(), browser.p256dh, browser.auth)
        val b = WebPushCrypto.encrypt("hi".toByteArray(), browser.p256dh, browser.auth)
        assertTrue(!a.contentEquals(b))
        assertContentEquals("hi".toByteArray(), browser.decrypt(a))
    }

    @Suppress("unused")
    private fun MockRequestHandleScope.unused(): HttpResponseData? = null
}

private operator fun ByteArray.plus(b: Int): ByteArray = this + b.toByte()
