package app.klean.backend.admin

import app.klean.backend.claimDevice
import app.klean.backend.jsonClient
import app.klean.backend.kleanTest
import app.klean.backend.login
import app.klean.backend.sha256Hex
import app.klean.backend.tempDataDir
import app.klean.backend.testConfig
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.SyncRecord
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.sql.DriverManager
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupTest {
    @Test fun backupIsAZipWithAConsistentDatabaseAndTheBlobs() = kleanTest { s ->
        val c = jsonClient()
        val web = c.login()
        val phone = c.claimDevice(web.token, "Pixel")
        val rec = SyncRecord("task", "t1", 1, "p", false, buildJsonObject { put("title", "Buy milk") })
        c.post("/api/v1/sync/push") { bearerAuth(phone.token); contentType(ContentType.Application.Json); setBody(PushRequest("p", listOf(rec))) }
        val photo = "a photo".toByteArray()
        c.put("/api/v1/blobs/${sha256Hex(photo)}") { bearerAuth(phone.token); setBody(photo) }

        assertEquals(HttpStatusCode.Forbidden, c.post("/api/v1/admin/backup") { bearerAuth(phone.token) }.status)

        val r = c.post("/api/v1/admin/backup") { bearerAuth(web.token) }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("application/zip", r.headers[HttpHeaders.ContentType])
        assertTrue(r.headers[HttpHeaders.ContentDisposition]!!.contains("klean-backup-"))

        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(r.readRawBytes().inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes()
            }
        }
        assertContentEquals(photo, entries.getValue("blobs/${sha256Hex(photo).take(2)}/${sha256Hex(photo)}"))
        val dbFile = File(tempDataDir(), "restored.sqlite").apply { writeBytes(entries.getValue("klean.sqlite")) }
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().executeQuery("SELECT json FROM records WHERE id = 't1'").use { rs ->
                assertTrue(rs.next())
                assertTrue("Buy milk" in rs.getString(1))
            }
        }
        // The snapshot file is cleaned up afterwards.
        assertTrue(File(s.config.dataDir, "backups").listFiles().orEmpty().none { it.name.endsWith(".sqlite") })
    }

    @Test fun postgresGetsAPgDumpHint() = kleanTest(config = testConfig()) { s ->
        val c = jsonClient()
        val web = c.login()
        s.backup.forcePostgresForTest = true
        val r = c.post("/api/v1/admin/backup") { bearerAuth(web.token) }
        assertEquals(HttpStatusCode.NotImplemented, r.status)
        assertTrue("pg_dump" in r.bodyAsText())
    }
}
