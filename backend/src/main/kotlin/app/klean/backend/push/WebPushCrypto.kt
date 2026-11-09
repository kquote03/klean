package app.klean.backend.push

import app.klean.backend.b64Url
import app.klean.backend.randomBytes
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Web Push message encryption (RFC 8291, `aes128gcm` from RFC 8188) and VAPID (RFC 8292), with JDK crypto only.
 * Chosen over `nl.martijndwars:web-push`, which is barely maintained and pulls in an extra HTTP client and JOSE
 * library; this is ~100 lines over standard primitives and is tested by decrypting from the receiver's side.
 */
object WebPushCrypto {
    private const val RECORD_SIZE = 4096

    private val p256: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** Uncompressed point, 65 bytes: 0x04 || X || Y. */
    fun encodePublic(key: ECPublicKey): ByteArray {
        fun fixed(n: BigInteger): ByteArray {
            val b = n.toByteArray()
            return when {
                b.size == 32 -> b
                b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                else -> ByteArray(32 - b.size) + b
            }
        }
        return byteArrayOf(4) + fixed(key.w.affineX) + fixed(key.w.affineY)
    }

    fun decodePublic(raw: ByteArray): PublicKey {
        require(raw.size == 65 && raw[0].toInt() == 4) { "expected an uncompressed P-256 point" }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256))
    }

    fun decodePrivate(pkcs8: ByteArray): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** Encrypts [payload] for a subscription (`keys.p256dh` raw point, `keys.auth` 16 bytes). One record. */
    fun encrypt(payload: ByteArray, uaPublic: ByteArray, authSecret: ByteArray): ByteArray {
        require(payload.size <= RECORD_SIZE - 17 - 86) { "payload too large" }
        val ua = decodePublic(uaPublic)
        val eph = generateKeyPair()
        val asPublic = encodePublic(eph.public as ECPublicKey)
        val ecdh = KeyAgreement.getInstance("ECDH").apply { init(eph.private); doPhase(ua, true) }.generateSecret()

        val prkKey = hmac(authSecret, ecdh)
        val ikm = hmac(prkKey, "WebPush: info".toByteArray() + 0 + uaPublic + asPublic + 1)
        val salt = randomBytes(16)
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(12)

        val padded = payload + 2 // last-record delimiter, no extra padding
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
        }.doFinal(padded)

        return ByteBuffer.allocate(16 + 4 + 1 + asPublic.size + ciphertext.size)
            .put(salt).putInt(RECORD_SIZE).put(asPublic.size.toByte()).put(asPublic).put(ciphertext)
            .array()
    }

    /** `Authorization: vapid t=<ES256 JWT>, k=<public key>` for [audience] (the push service origin). */
    fun vapidHeader(audience: String, subject: String, expiresAtSec: Long, privateKey: PrivateKey, publicKey: ByteArray): String {
        val header = """{"typ":"JWT","alg":"ES256"}""".toByteArray().b64Url()
        val claims = """{"aud":"$audience","exp":$expiresAtSec,"sub":"$subject"}""".toByteArray().b64Url()
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initSign(privateKey); update("$header.$claims".toByteArray())
        }.sign()
        return "vapid t=$header.$claims.${signature.b64Url()}, k=${publicKey.b64Url()}"
    }
}

private operator fun ByteArray.plus(b: Int): ByteArray = this + b.toByte()
