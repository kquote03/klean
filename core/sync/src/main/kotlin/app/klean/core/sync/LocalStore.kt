package app.klean.core.sync

import android.util.Log
import androidx.room.PooledConnection
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SyncCursorEntity
import app.klean.shared.sync.SyncRecord
import app.klean.shared.sync.lww
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** A local row ready to push, with the `change_seq` that orders it. */
data class LocalRow(val record: SyncRecord, val changeSeq: Long)

enum class ApplyResult {
    /** The remote version was written. */
    APPLIED,
    /** The local version is the same or newer by [lww]: nothing to do. */
    OLDER,
    /** This phone has an edit the server hasn't seen yet; it wins until it is pushed. */
    KEPT_LOCAL,
    /** Not a synced table here (a newer client's table), or the record can't be stored. Skipped. */
    SKIPPED,
}

/** One synced table: reads changed rows as records and applies remote records with [lww]. */
interface SyncTable {
    val spec: SyncTableSpec
    suspend fun changedSince(seq: Long, limit: Int): List<LocalRow>
    suspend fun apply(record: SyncRecord, lastPushSeq: Long): ApplyResult
}

/** What the engine needs from the local database. */
interface LocalStore {
    val tables: List<SyncTable>
    suspend fun cursor(): SyncCursorEntity
    suspend fun saveCursor(cursor: SyncCursorEntity)

    /** The [limit] lowest-`change_seq` rows above [seq], across every table. */
    suspend fun changedSince(seq: Long, limit: Int): List<LocalRow> =
        tables.flatMap { it.changedSince(seq, limit) }.sortedBy { it.changeSeq }.take(limit)

    suspend fun apply(record: SyncRecord, lastPushSeq: Long): ApplyResult =
        tables.firstOrNull { it.spec.name == record.table }?.apply(record, lastPushSeq) ?: ApplyResult.SKIPPED
}

/** [LocalStore] over the Room database. */
class RoomLocalStore(private val db: KleanDatabase, specs: List<SyncTableSpec> = SyncTables.ALL) : LocalStore {
    override val tables: List<SyncTable> = specs.map { RoomSyncTable(db, it) }
    override suspend fun cursor(): SyncCursorEntity = db.syncCursorDao().get() ?: SyncCursorEntity()
    override suspend fun saveCursor(cursor: SyncCursorEntity) = db.syncCursorDao().upsert(cursor)
}

/**
 * Generic over any table with `SyncColumns`: rows are read with `SELECT *` and written with an upsert of the
 * columns the record carries, so one adapter serves every entity without per-entity mapping code.
 * Pulled rows get `change_seq = 0`, so they never count as local edits to push.
 */
class RoomSyncTable(private val db: KleanDatabase, override val spec: SyncTableSpec) : SyncTable {
    @Volatile private var columns: List<String>? = null

    override suspend fun changedSince(seq: Long, limit: Int): List<LocalRow> = db.useReaderConnection { c ->
        c.usePrepared("SELECT * FROM ${spec.name} WHERE change_seq > ? ORDER BY change_seq LIMIT ?") { s ->
            s.bindLong(1, seq)
            s.bindLong(2, limit.toLong())
            buildList { while (s.step()) add(row(s)) }
        }
    }

    private fun row(s: SQLiteStatement): LocalRow {
        var changeSeq = 0L
        val json = buildJsonObject {
            for (i in 0 until s.getColumnCount()) {
                val name = s.getColumnName(i)
                if (name == "change_seq") {
                    changeSeq = s.getLong(i)
                    continue
                }
                if (name in spec.localColumns) continue
                put(name, value(s, i, name))
            }
        }
        val record = SyncRecord(
            table = spec.name,
            id = (json["id"] as JsonPrimitive).content,
            updatedAt = (json["updated_at"] as JsonPrimitive).longOrNull ?: 0L,
            deviceId = (json["device_id"] as JsonPrimitive).content,
            deleted = (json["deleted"] as? JsonPrimitive)?.booleanOrNull ?: false,
            json = json,
        )
        return LocalRow(record, changeSeq)
    }

    private fun value(s: SQLiteStatement, i: Int, name: String): JsonElement = when (s.getColumnType(i)) {
        SQLITE_DATA_NULL -> JsonNull
        SQLITE_DATA_INTEGER -> if (spec.isBoolean(name)) JsonPrimitive(s.getLong(i) != 0L) else JsonPrimitive(s.getLong(i))
        SQLITE_DATA_FLOAT -> JsonPrimitive(s.getDouble(i))
        SQLITE_DATA_BLOB -> JsonPrimitive(Base64.getEncoder().encodeToString(s.getBlob(i)))
        else -> JsonPrimitive(s.getText(i))
    }

    override suspend fun apply(record: SyncRecord, lastPushSeq: Long): ApplyResult {
        val result = try {
            db.useWriterConnection { c -> c.immediateTransaction { applyIn(this, record, lastPushSeq) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A record this schema can't hold (e.g. a missing NOT NULL column) must not stop the stream.
            Log.w(TAG, "sync: skipped ${record.table}/${record.id}: ${e.message}")
            ApplyResult.SKIPPED
        }
        if (result == ApplyResult.APPLIED) db.invalidationTracker.refreshAsync()
        return result
    }

    private suspend fun applyIn(c: PooledConnection, r: SyncRecord, lastPushSeq: Long): ApplyResult {
        val cols = columns ?: c.usePrepared("PRAGMA table_info(${spec.name})") { s ->
            buildList { while (s.step()) add(s.getText(1)) }
        }.also { columns = it }
        val local = c.usePrepared("SELECT updated_at, device_id, change_seq FROM ${spec.name} WHERE id = ?") { s ->
            s.bindText(1, r.id)
            if (s.step()) Triple(s.getLong(0), s.getText(1), s.getLong(2)) else null
        }
        if (local != null) {
            if (local.third > lastPushSeq) return ApplyResult.KEPT_LOCAL
            val mine = SyncRecord(r.table, r.id, local.first, local.second, false, EMPTY)
            if (lww(mine, r) !== r) return ApplyResult.OLDER
        }
        val values = LinkedHashMap<String, JsonElement>()
        for (col in cols) {
            when (col) {
                "id" -> values[col] = JsonPrimitive(r.id)
                "updated_at" -> values[col] = JsonPrimitive(r.updatedAt)
                "device_id" -> values[col] = JsonPrimitive(r.deviceId)
                "deleted" -> values[col] = JsonPrimitive(r.deleted)
                "change_seq" -> values[col] = JsonPrimitive(0L)
                in spec.localColumns -> Unit
                else -> r.json[col]?.let { values[col] = it }
            }
        }
        val names = values.keys.toList()
        val sql = buildString {
            append("INSERT INTO ${spec.name} (").append(names.joinToString(",") { "`$it`" }).append(") VALUES (")
            append(names.joinToString(",") { "?" }).append(") ON CONFLICT(id) DO UPDATE SET ")
            append(names.filter { it != "id" }.joinToString(",") { "`$it` = excluded.`$it`" })
        }
        c.usePrepared(sql) { s ->
            names.forEachIndexed { i, n -> bind(s, i + 1, values.getValue(n)) }
            s.step()
        }
        return ApplyResult.APPLIED
    }

    private fun bind(s: SQLiteStatement, index: Int, v: JsonElement) {
        val p = v as? JsonPrimitive
        when {
            p == null -> s.bindText(index, v.toString()) // nested JSON: stored as JSON text, like Room's TEXT columns
            p is JsonNull -> s.bindNull(index)
            p.isString -> s.bindText(index, p.content)
            p.booleanOrNull != null -> s.bindLong(index, if (p.booleanOrNull == true) 1 else 0)
            p.longOrNull != null -> s.bindLong(index, p.longOrNull!!)
            p.doubleOrNull != null -> s.bindDouble(index, p.doubleOrNull!!)
            else -> s.bindText(index, p.content)
        }
    }

    private companion object {
        const val TAG = "Klean"
        val EMPTY = JsonObject(emptyMap())
    }
}
