package app.klean.backend.blobs

import app.klean.backend.ApiException
import app.klean.backend.KleanConfig
import app.klean.backend.db.BlobsTable
import app.klean.backend.db.KleanDb
import app.klean.backend.toHex
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.time.Clock

data class BlobInfo(val sha256: String, val size: Long, val file: File, val contentType: String?)

/** Content-addressed files under `<data>/blobs/<first 2 hex>/<sha256>`. */
class BlobStore(private val db: KleanDb, private val config: KleanConfig, private val clock: Clock) {

    suspend fun find(sha: String): BlobInfo? {
        requireSha(sha)
        val row = db.read { BlobsTable.selectAll().where { BlobsTable.sha256 eq sha }.singleOrNull() } ?: return null
        val file = File(config.blobDir, row[BlobsTable.path])
        return if (file.isFile) BlobInfo(sha, row[BlobsTable.size], file, row[BlobsTable.contentType]) else null
    }

    /** Streams [input] to a temp file while hashing; only a matching hash is moved into place. Returns true if new. */
    suspend fun put(sha: String, input: InputStream, contentType: String?): Boolean {
        requireSha(sha)
        if (find(sha) != null) {
            withContext(Dispatchers.IO) { input.use { it.skip(Long.MAX_VALUE) } }
            return false
        }
        val tmpDir = File(config.blobDir, "tmp").apply { mkdirs() }
        val tmp = File.createTempFile("upload-", ".part", tmpDir)
        try {
            val actual = withContext(Dispatchers.IO) {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                input.use { inp ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > config.blobMaxBytes) throw ApiException(HttpStatusCode.PayloadTooLarge, "blob too large")
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                digest.digest().toHex() to total
            }
            if (actual.first != sha) throw ApiException(HttpStatusCode.BadRequest, "sha256 mismatch")
            val rel = "${sha.take(2)}/$sha"
            val dest = File(config.blobDir, rel).apply { parentFile.mkdirs() }
            withContext(Dispatchers.IO) { Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            db.write {
                BlobsTable.insertIgnore {
                    it[sha256] = sha
                    it[size] = actual.second
                    it[path] = rel
                    it[BlobsTable.contentType] = contentType?.take(100)
                    it[createdAt] = clock.now().toEpochMilliseconds()
                }
            }
            return true
        } finally {
            tmp.delete()
        }
    }

    private fun requireSha(sha: String) {
        if (!SHA256.matches(sha)) throw ApiException(HttpStatusCode.BadRequest, "expected a lowercase sha256 hex")
    }

    companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}
