/*
 * The sync wire protocol shared by the Android client (M25), the backend (M24) and, by mirror, the web (M26).
 * Every syncable row travels as a generic [SyncRecord]; the server stores it without per-entity code.
 */
package app.klean.shared.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One row of one syncable table. */
@Serializable
data class SyncRecord(
    /** The Room table name, e.g. "task", "food_entry". */
    val table: String,
    val id: String,
    /** UTC epoch ms of the last change; the LWW clock. */
    val updatedAt: Long,
    /** The device that made the change; the LWW tie-breaker. */
    val deviceId: String,
    /** Tombstone. Rows are never hard-deleted. */
    val deleted: Boolean,
    /** The row's columns, snake_case, excluding `change_seq` (device-local). */
    val json: JsonObject,
)

@Serializable
data class PushRequest(val deviceId: String, val records: List<SyncRecord>)

/** [accepted] counts records that won LWW and were stored; [serverCursor] is the server's latest sequence. */
@Serializable
data class PushResponse(val accepted: Int, val serverCursor: Long)

/** Records with a server sequence above the requested cursor, oldest first. Pass [cursor] back next time. */
@Serializable
data class PullResponse(val records: List<SyncRecord>, val cursor: Long, val hasMore: Boolean)

/**
 * Last-write-wins: the higher `updatedAt` wins; ties are broken by `deviceId` in lexical order, so every
 * participant picks the same winner regardless of the order it sees versions in. An identical version keeps
 * [local] (so re-pushing is a no-op).
 */
fun lww(local: SyncRecord?, remote: SyncRecord): SyncRecord =
    if (local == null || remote.updatedAt > local.updatedAt ||
        (remote.updatedAt == local.updatedAt && remote.deviceId > local.deviceId)
    ) remote else local

/** Table names accepted on the wire: lowercase snake_case, as in Room. */
val SYNC_TABLE_NAME = Regex("^[a-z][a-z0-9_]{0,63}$")
