package app.klean.backend.db

import app.klean.backend.KleanConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.postgresql.ds.PGSimpleDataSource
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.Closeable
import java.sql.Connection
import javax.sql.DataSource

/**
 * Exposed over JDBC. SQLite (default) or Postgres, chosen by [KleanConfig.dbUrl].
 *
 * All writes go through [write], which holds one process-wide mutex. That keeps `server_seq` gap-free and in
 * commit order (a pull never skips a write that commits later with a lower sequence), and avoids SQLite
 * SQLITE_BUSY upgrades. The server is single-instance by design.
 */
class KleanDb private constructor(val database: Database, val isSqlite: Boolean, private val dataSource: DataSource) : Closeable {
    private val writeLock = Mutex()

    suspend fun <T> read(block: JdbcTransaction.() -> T): T = withContext(Dispatchers.IO) { transaction(database) { block() } }

    suspend fun <T> write(block: JdbcTransaction.() -> T): T = writeLock.withLock {
        withContext(Dispatchers.IO) { transaction(database) { block() } }
    }

    /** Blocking variant for start-up work. */
    fun <T> blocking(block: JdbcTransaction.() -> T): T = transaction(database) { block() }

    suspend fun setting(key: String): String? = read {
        SettingsTable.selectAll().where { SettingsTable.key eq key }.singleOrNull()?.get(SettingsTable.value)
    }

    suspend fun putSetting(key: String, value: String) = write { upsertSetting(key, value) }

    fun JdbcTransaction.upsertSetting(key: String, value: String) {
        SettingsTable.upsert {
            it[SettingsTable.key] = key
            it[SettingsTable.value] = value
        }
    }

    /** For admin backup: a consistent SQLite snapshot via `VACUUM INTO`. */
    suspend fun vacuumInto(path: String) = write {
        require(isSqlite) { "VACUUM INTO needs SQLite" }
        exec("VACUUM INTO '${path.replace("'", "''")}'")
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    companion object {
        fun open(config: KleanConfig): KleanDb {
            config.dataDir.mkdirs()
            val sqlite = config.isSqlite
            val ds: DataSource = if (sqlite) {
                val sc = SQLiteConfig().apply {
                    setJournalMode(SQLiteConfig.JournalMode.WAL)
                    setBusyTimeout(10_000)
                    setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
                }
                SQLiteDataSource(sc).apply { url = config.dbUrl }
            } else {
                PGSimpleDataSource().apply {
                    setURL(config.dbUrl)
                    config.dbUser?.let { user = it }
                    config.dbPassword?.let { password = it }
                }
            }
            val db = Database.connect(
                datasource = ds,
                databaseConfig = DatabaseConfig {
                    if (sqlite) defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                },
            )
            transaction(db) { SchemaUtils.create(*ALL_TABLES) }
            return KleanDb(db, sqlite, ds)
        }
    }
}
