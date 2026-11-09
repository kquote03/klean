package app.klean.backend

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal val secureRandom = SecureRandom()

fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

fun sha256Hex(bytes: ByteArray): String = sha256(bytes).toHex()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun randomBytes(n: Int): ByteArray = ByteArray(n).also { secureRandom.nextBytes(it) }

fun ByteArray.b64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

fun String.fromB64Url(): ByteArray = Base64.getUrlDecoder().decode(trim().trimEnd('='))
