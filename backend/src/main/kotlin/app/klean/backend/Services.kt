package app.klean.backend

import app.klean.backend.auth.AuthService
import app.klean.backend.blobs.BlobStore
import app.klean.backend.dav.Dav
import app.klean.backend.db.KleanDb
import app.klean.backend.db.RecordStore
import app.klean.backend.push.PushService
import app.klean.backend.google.GoogleService
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import java.io.Closeable
import kotlin.time.Clock

/**
 * The server's object graph, built once at start (or per test with fakes).
 * [httpClient] is used for every outbound call (web push, Google, Hermes, AI upstream); tests pass a MockEngine.
 */
class Services(
    val config: KleanConfig,
    val db: KleanDb,
    val clock: Clock,
    val httpClient: HttpClient,
) : Closeable {
    val records = RecordStore(db)
    val auth = AuthService(db, config, clock)
    val blobs = BlobStore(db, config, clock)
    val dav = Dav(records, clock)
    val push = PushService(db, config, httpClient, clock)
    val google = GoogleService(db, config, httpClient, clock)

    override fun close() {
        httpClient.close()
        db.close()
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                // Streaming relays (Hermes SSE, AI streams) may stay open for long.
                requestTimeoutMillis = Long.MAX_VALUE
                socketTimeoutMillis = 5 * 60_000
            }
        }

        fun create(config: KleanConfig, clock: Clock = Clock.System, httpClient: HttpClient = defaultHttpClient()): Services {
            config.dataDir.mkdirs()
            val db = KleanDb.open(config)
            return Services(config, db, clock, httpClient).also {
                it.auth.ensureAdminPassword()
                it.push.ensureKeys()
            }
        }
    }
}
