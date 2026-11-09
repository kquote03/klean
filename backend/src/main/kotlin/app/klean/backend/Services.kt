package app.klean.backend

import app.klean.backend.auth.AuthService
import app.klean.backend.db.KleanDb
import app.klean.backend.db.RecordStore
import java.io.Closeable
import kotlin.time.Clock

/** The server's object graph, built once at start (or per test with fakes). */
class Services(
    val config: KleanConfig,
    val db: KleanDb,
    val clock: Clock,
) : Closeable {
    val records = RecordStore(db)
    val auth = AuthService(db, config, clock)

    override fun close() {
        db.close()
    }

    companion object {
        fun create(config: KleanConfig, clock: Clock = Clock.System): Services {
            config.dataDir.mkdirs()
            val db = KleanDb.open(config)
            return Services(config, db, clock).also { it.auth.ensureAdminPassword() }
        }
    }
}
