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
        return SyncColumns(
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            deviceId = prefs.deviceId(),
            deleted = deleted,
            changeSeq = db.changeCounterDao().next(),
        )
    }
}
