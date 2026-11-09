package app.klean.backend.db

import app.klean.backend.KleanJson
import app.klean.backend.sha256Hex
import app.klean.shared.sync.PullResponse
import app.klean.shared.sync.SYNC_TABLE_NAME
import app.klean.shared.sync.SyncRecord
import app.klean.shared.sync.lww
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

data class StoredRecord(val record: SyncRecord, val serverSeq: Long, val etag: String)

data class ApplyResult(val accepted: Int, val rejected: Int, val serverCursor: Long)

/** Generic LWW storage for every synced table. Rows are never deleted; tombstones sync like any version. */
class RecordStore(private val db: KleanDb) {

    suspend fun get(table: String, id: String): StoredRecord? = db.read { find(table, id) }

    /**
     * Applies [records] with [lww] against what's stored. Winners get a fresh `server_seq`; losers (older or
     * identical versions) are counted neither as accepted nor as rejected. [ApplyResult.rejected] counts invalid
     * records (bad table name or id).
     */
    suspend fun apply(records: List<SyncRecord>): ApplyResult = db.write {
        var seq = currentMaxSeq()
        var accepted = 0
        var rejected = 0
        for (incoming in records) {
            if (!SYNC_TABLE_NAME.matches(incoming.table) || incoming.id.isBlank() || incoming.id.length > 64 ||
                incoming.deviceId.length > 64
            ) {
                rejected++
                continue
            }
            val existing = find(incoming.table, incoming.id)
            val winner = lww(existing?.record, incoming)
            if (winner === incoming && existing?.record != incoming) {
                seq += 1
                store(incoming, seq)
                accepted++
            }
        }
        ApplyResult(accepted, rejected, seq)
    }

    /** Server-side writes (DAV PUT/DELETE). The caller has already merged; this just stamps a new seq. */
    suspend fun put(record: SyncRecord): StoredRecord = db.write {
        val seq = currentMaxSeq() + 1
        store(record, seq)
        find(record.table, record.id)!!
    }

    suspend fun pull(cursor: Long, limit: Int, tables: Set<String>? = null): PullResponse = db.read {
        val rows = RecordsTable.selectAll()
            .where {
                val base = RecordsTable.serverSeq greater cursor
                if (tables.isNullOrEmpty()) base else base and (RecordsTable.tbl inList tables)
            }
            .orderBy(RecordsTable.serverSeq to SortOrder.ASC)
            .limit(limit + 1)
            .map { it.toStored() }
        val page = rows.take(limit)
        PullResponse(page.map { it.record }, page.lastOrNull()?.serverSeq ?: cursor, rows.size > limit)
    }

    suspend fun list(table: String, includeDeleted: Boolean): List<StoredRecord> = db.read {
        RecordsTable.selectAll()
            .where { if (includeDeleted) RecordsTable.tbl eq table else (RecordsTable.tbl eq table) and (RecordsTable.deleted eq false) }
            .orderBy(RecordsTable.serverSeq to SortOrder.ASC)
            .map { it.toStored() }
    }

    /** Every version of [table] written after [seq], tombstones included (DAV sync-collection). */
    suspend fun changesSince(table: String, seq: Long): List<StoredRecord> = db.read {
        RecordsTable.selectAll()
            .where { (RecordsTable.tbl eq table) and (RecordsTable.serverSeq greater seq) }
            .orderBy(RecordsTable.serverSeq to SortOrder.ASC)
            .map { it.toStored() }
    }

    suspend fun maxSeq(table: String? = null): Long = db.read {
        val max = RecordsTable.serverSeq.max()
        val q = RecordsTable.select(max)
        (if (table == null) q else q.where { RecordsTable.tbl eq table }).single()[max] ?: 0L
    }

    private fun JdbcTransaction.currentMaxSeq(): Long {
        val max = RecordsTable.serverSeq.max()
        return RecordsTable.select(max).single()[max] ?: 0L
    }

    private fun JdbcTransaction.find(table: String, id: String): StoredRecord? =
        RecordsTable.selectAll().where { (RecordsTable.tbl eq table) and (RecordsTable.id eq id) }.singleOrNull()?.toStored()

    private fun JdbcTransaction.store(record: SyncRecord, seq: Long) {
        RecordsTable.upsert {
            it[tbl] = record.table
            it[id] = record.id
            it[updatedAt] = record.updatedAt
            it[deviceId] = record.deviceId
            it[deleted] = record.deleted
            it[json] = KleanJson.encodeToString(JsonObject.serializer(), record.json)
            it[serverSeq] = seq
            it[etag] = etagOf(record)
        }
    }

    private fun ResultRow.toStored() = StoredRecord(
        record = SyncRecord(
            table = this[RecordsTable.tbl],
            id = this[RecordsTable.id],
            updatedAt = this[RecordsTable.updatedAt],
            deviceId = this[RecordsTable.deviceId],
            deleted = this[RecordsTable.deleted],
            json = KleanJson.decodeFromString(JsonObject.serializer(), this[RecordsTable.json]),
        ),
        serverSeq = this[RecordsTable.serverSeq],
        etag = this[RecordsTable.etag],
    )

    companion object {
        fun etagOf(record: SyncRecord): String = sha256Hex(KleanJson.encodeToString(SyncRecord.serializer(), record).toByteArray())
    }
}
