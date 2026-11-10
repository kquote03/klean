package app.klean.core.data.sync

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.prefs.DevicePrefs
import javax.inject.Inject
import kotlin.time.Clock

/** Produces the sync columns for every write. The only place `change_seq` is issued. */
class SyncStamper @Inject constructor(
    private val db: KleanDatabase,
    private val prefs: DevicePrefs,
    private val clock: Clock,
) {
    /** Call inside `db.transact {}` so the counter and the row commit together. */
    suspend fun stamp(previous: SyncColumns? = null, deleted: Boolean = previous?.deleted ?: false): SyncColumns {
        val now = clock.now().toEpochMilliseconds()
        val me = prefs.deviceId()
        // Sync (M25): an edit made on top of another device's version must win LWW against it, even when this
        // phone's clock is behind that device's; otherwise the server keeps the old version and the phones diverge.
        val updatedAt = if (previous != null && previous.deviceId != me) maxOf(now, previous.updatedAt + 1) else now
        return SyncColumns(
            createdAt = previous?.createdAt ?: now,
            updatedAt = updatedAt,
            deviceId = me,
            deleted = deleted,
            changeSeq = db.changeCounterDao().next(),
        )
    }
}
