package app.klean.backend.auth

import app.klean.backend.randomBytes
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.util.Base64

/**
 * Argon2id (BouncyCastle) in PHC string format: `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`.
 * Parameters are the OWASP 2024 minimum for argon2id. Only the admin password uses this; device tokens are
 * 32 random bytes, so a plain sha256 of them is enough (see [Tokens]).
 */
object Passwords {
    private const val MEMORY_KIB = 19_456
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val HASH_LEN = 32
    private val b64 = Base64.getEncoder().withoutPadding()
    private val b64d = Base64.getDecoder()

    fun hash(password: String): String {
        val salt = randomBytes(16)
        val out = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_LEN)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$${b64.encodeToString(salt)}\$${b64.encodeToString(out)}"
    }

    fun verify(password: String, encoded: String): Boolean = runCatching {
        val parts = encoded.split('$')
        // ["", "argon2id", "v=19", "m=…,t=…,p=…", salt, hash]
        if (parts.size != 6 || parts[1] != "argon2id" || parts[2] != "v=19") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val salt = b64d.decode(parts[4])
        val expected = b64d.decode(parts[5])
        val actual = derive(password, salt, params.getValue("m"), params.getValue("t"), params.getValue("p"), expected.size)
        MessageDigest.isEqual(expected, actual)
    }.getOrDefault(false)

    private fun derive(password: String, salt: ByteArray, m: Int, t: Int, p: Int, len: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(m)
            .withIterations(t)
            .withParallelism(p)
            .withSalt(salt)
            .build()
        val gen = Argon2BytesGenerator().apply { init(params) }
        return ByteArray(len).also { gen.generateBytes(password.toByteArray(Charsets.UTF_8), it) }
    }
}
