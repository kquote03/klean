package app.klean.core.sync

import android.util.Log
import app.klean.shared.sync.PushRequest
import app.klean.shared.sync.SyncRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** Told about remote records after they were written locally (e.g. cancel a prompt answered elsewhere). */
fun interface SyncApplyListener {
    suspend fun onApplied(records: List<SyncRecord>)
}

/** What one run did. [complete] is false when a page was left for the next run (see [SyncEngine.sync]). */
data class SyncReport(val pushed: Int, val pulled: Int, val applied: Int, val complete: Boolean = true)

/**
 * One sync run: push, then pull.
 * - **Push:** rows with `change_seq > sync_cursor.last_push_seq`, lowest first, in pages. Blob files are uploaded
 *   first (`HEAD`, then `PUT` only if missing), and the record carries their hashes under [SyncTables.BLOB_KEY].
 *   After each accepted page `last_push_seq` moves to the page's highest `change_seq`.
 * - **Pull:** from `sync_cursor.cursor`, applying each record with `lww`. A row this phone changed since its last
 *   push keeps the local version: the page's cursor is then not saved, so the next run (which pushes first)
 *   reads the page again and settles it with `lww` against what the server kept.
 * - `dav_` keys are server-only and dropped; records for unknown tables are skipped.
 * Never called on the main thread's write path: local writes only bump `change_seq`; this runs from WorkManager.
 * Any network error propagates; cursors saved so far stay, so the next attempt resumes.
 */
class SyncEngine(
    private val store: LocalStore,
    private val blobs: BlobFiles,
    private val deviceId: suspend () -> String,
    private val listeners: Set<SyncApplyListener> = emptySet(),
    private val pageSize: Int = 500,
) {
    private val mutex = Mutex()

    suspend fun sync(api: SyncApi): SyncReport = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pushed = push(api)
            val (pulled, applied, complete) = pull(api)
            SyncReport(pushed, pulled, applied, complete)
        }
    }

    private suspend fun push(api: SyncApi): Int {
        val me = deviceId()
        var total = 0
        while (true) {
            val cursor = store.cursor()
            val rows = store.changedSince(cursor.lastPushSeq, pageSize)
            if (rows.isEmpty()) return total
            val records = rows.map { withBlobs(api, it.record) }
            api.push(PushRequest(me, records))
            store.saveCursor(store.cursor().copy(lastPushSeq = rows.maxOf { it.changeSeq }))
            total += records.size
            if (rows.size < pageSize) return total
        }
    }

    /** Uploads the record's blob files and adds their hashes. A missing file is sent without a hash. */
    private suspend fun withBlobs(api: SyncApi, r: SyncRecord): SyncRecord {
        val spec = SyncTables.byName[r.table] ?: return r
        if (spec.blobs.isEmpty()) return r
        val hashes = mutableMapOf<String, String>()
        for ((col, dir) in spec.blobs) {
            val name = (r.json[col] as? JsonPrimitive)?.contentOrNull ?: continue
            val file = blobs.file(dir, name)?.takeIf { it.isFile } ?: continue
            val bytes = file.readBytes()
            val sha = BlobFiles.sha256(bytes)
            if (!api.hasBlob(sha)) api.putBlob(sha, bytes, BlobFiles.contentType(name))
            hashes[col] = sha
        }
        if (hashes.isEmpty()) return r
        val json = JsonObject(r.json + (SyncTables.BLOB_KEY to buildJsonObject { hashes.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }))
        return r.copy(json = json)
    }

    private suspend fun pull(api: SyncApi): Triple<Int, Int, Boolean> {
        var pulled = 0
        var appliedCount = 0
        while (true) {
            val cursor = store.cursor()
            val page = api.pull(cursor.cursor, pageSize)
            pulled += page.records.size
            var leaveForLater = false
            val applied = mutableListOf<SyncRecord>()
            for (raw in page.records) {
                val r = raw.copy(json = JsonObject(raw.json.filterKeys { !it.startsWith(SyncTables.SERVER_PREFIX) }))
                when (store.apply(r, cursor.lastPushSeq)) {
                    ApplyResult.APPLIED -> {
                        applied += r
                        fetchBlobs(api, r)
                    }
                    // Also for versions applied by an earlier, interrupted run of this page.
                    ApplyResult.OLDER -> fetchBlobs(api, r)
                    ApplyResult.KEPT_LOCAL -> leaveForLater = true
                    ApplyResult.SKIPPED -> Unit
                }
            }
            appliedCount += applied.size
            if (applied.isNotEmpty()) notify(applied)
            if (leaveForLater) return Triple(pulled, appliedCount, false)
            store.saveCursor(store.cursor().copy(cursor = page.cursor))
            if (!page.hasMore || page.records.isEmpty()) return Triple(pulled, appliedCount, true)
        }
    }

    /**
     * Downloads blob files a record names and this phone lacks. A network error propagates before the page's
     * cursor is saved, so the page (and this download) is retried. A blob the server doesn't have is skipped.
     */
    private suspend fun fetchBlobs(api: SyncApi, r: SyncRecord) {
        val spec = SyncTables.byName[r.table] ?: return
        if (spec.blobs.isEmpty() || r.deleted) return
        val hashes = r.json[SyncTables.BLOB_KEY] as? JsonObject ?: return
        for ((col, dir) in spec.blobs) {
            val name = (r.json[col] as? JsonPrimitive)?.contentOrNull ?: continue
            val sha = (hashes[col] as? JsonPrimitive)?.contentOrNull ?: continue
            val file = blobs.file(dir, name) ?: continue
            if (file.isFile) continue
            val bytes = api.getBlob(sha)
            if (bytes == null || !blobs.write(file, bytes, sha)) Log.w("Klean", "sync: blob for ${r.table}/${r.id} unavailable")
        }
    }

    private suspend fun notify(records: List<SyncRecord>) {
        for (l in listeners) {
            try {
                l.onApplied(records)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "sync listener failed", e)
            }
        }
    }
}
