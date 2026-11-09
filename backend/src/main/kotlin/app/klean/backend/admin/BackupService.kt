package app.klean.backend.admin

import app.klean.backend.KleanConfig
import app.klean.backend.db.KleanDb
import app.klean.backend.requireAdmin
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

@Serializable data class BackupHint(val error: String, val hint: String)

/**
 * `POST /admin/backup`: a zip of a consistent SQLite snapshot (`VACUUM INTO`, taken under the write lock) plus
 * every blob. With Postgres it answers 501 and a `pg_dump` hint instead.
 */
class BackupService(private val db: KleanDb, private val config: KleanConfig, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(BackupService::class.java)

    internal var forcePostgresForTest = false

    private val isSqlite get() = db.isSqlite && !forcePostgresForTest

    suspend fun snapshot(): File {
        val dir = File(config.dataDir, "backups").apply { mkdirs() }
        val file = File(dir, "snapshot-${clock.now().toEpochMilliseconds()}.sqlite")
        file.delete()
        db.vacuumInto(file.absolutePath)
        return file
    }

    fun writeZip(snapshot: File, out: java.io.OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("klean.sqlite"))
            snapshot.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            val blobs = config.blobDir
            if (blobs.isDirectory) {
                blobs.walkTopDown()
                    .filter { it.isFile && it.parentFile.name != "tmp" }
                    .forEach { f ->
                        zip.putNextEntry(ZipEntry("blobs/" + f.relativeTo(blobs).invariantSeparatorsPath))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
        }
    }

    fun routes(route: Route) = with(route) {
        post("/admin/backup") {
            call.requireAdmin()
            if (!isSqlite) {
                call.respond(HttpStatusCode.NotImplemented, BackupHint(
                    error = "backup over HTTP needs SQLite",
                    hint = "Postgres: run `pg_dump --format=custom klean > klean.dump` and back up the /data/blobs directory.",
                ))
                return@post
            }
            val snap = snapshot()
            val name = "klean-backup-${clock.now().toString().replace(":", "").substringBefore('.')}.zip"
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name).toString())
            log.info("backup started ({} bytes db)", snap.length())
            call.respondOutputStream(ContentType.Application.Zip) {
                try {
                    writeZip(snap, this)
                } finally {
                    snap.delete()
                }
            }
        }
    }
}
