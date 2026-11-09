package app.klean.backend.db

import org.jetbrains.exposed.v1.core.Table

/**
 * One generic table for every synced client entity, so a new Room table syncs without server code.
 * `server_seq` is a server-wide monotonic sequence assigned to each accepted write; clients pull by it.
 */
object RecordsTable : Table("records") {
    /** The client table name. (The Kotlin name avoids clashing with [Table.tableName].) */
    val tbl = varchar("table_name", 64)
    val id = varchar("id", 64)
    val updatedAt = long("updated_at")
    val deviceId = varchar("device_id", 64)
    val deleted = bool("deleted")
    val json = text("json")
    val serverSeq = long("server_seq").index()
    /** sha256 hex of the serialised record; the DAV ETag. */
    val etag = varchar("etag", 64)
    override val primaryKey = PrimaryKey(tbl, id)
}

object DevicesTable : Table("devices") {
    val id = varchar("id", 64)
    val name = varchar("name", 200)
    /** ANDROID, WEAR, WEB or DAV (an app password for DAV clients). */
    val platform = varchar("platform", 16)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val admin = bool("admin").default(false)
    val createdAt = long("created_at")
    val lastSeenAt = long("last_seen_at")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(id)
}

object PairCodesTable : Table("pair_codes") {
    val codeHash = varchar("code_hash", 64)
    val expiresAt = long("expires_at")
    val used = bool("used").default(false)
    override val primaryKey = PrimaryKey(codeHash)
}

object BlobsTable : Table("blobs") {
    val sha256 = varchar("sha256", 64)
    val size = long("size")
    /** Relative to the blob directory. */
    val path = varchar("path", 200)
    val contentType = varchar("content_type", 100).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(sha256)
}

object PushSubscriptionsTable : Table("push_subscriptions") {
    val endpoint = varchar("endpoint", 1000)
    val deviceId = varchar("device_id", 64).index()
    val p256dh = varchar("p256dh", 200)
    val auth = varchar("auth", 100)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(endpoint)
}

object GoogleTokensTable : Table("google_tokens") {
    /** "default": one Google account per server for now. */
    val id = varchar("id", 64)
    val refreshToken = text("refresh_token")
    val scope = text("scope")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** Server-side key/value settings: the admin password hash, VAPID keys, OAuth state. */
object SettingsTable : Table("settings") {
    val key = varchar("key", 100)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}

val ALL_TABLES = arrayOf(RecordsTable, DevicesTable, PairCodesTable, BlobsTable, PushSubscriptionsTable, GoogleTokensTable, SettingsTable)
