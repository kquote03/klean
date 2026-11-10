package app.klean.feature.sleep.data

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.core.health.HealthAccess
import app.klean.core.health.HealthConnectGateway
import app.klean.core.health.canUse
import app.klean.feature.sleep.SleepImportSource
import app.klean.shared.health.HealthMerge
import app.klean.shared.health.Span
import app.klean.shared.id.UuidV7
import app.klean.shared.time.HOUR_MS
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Brings Health Connect's sleep sessions of the last [days] days into `sleep_session` (source
 * `HEALTH_CONNECT`, `hc_record_id`), once each. A manual night that a Health Connect night overlaps by more
 * than half is replaced: tombstoned, with a note saying why (plan M22). Klean never writes sleep there.
 */
@Singleton
class HealthConnectSleepImport @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards SleepChangeListener>,
) : SleepImportSource {
    private val dao get() = db.sleepDao()

    override suspend fun importRecent(days: Int): Int {
        gateway.refresh()
        if (!gateway.canUse(HealthAccess.READ_SLEEP)) return 0
        val now = clock.now().toEpochMilliseconds()
        val from = now - days * 24 * HOUR_MS
        val records = gateway.readSleep(from, now).filter { it.origin != gateway.ownPackage && it.end > it.start }
        if (records.isEmpty()) return 0
        // Read a day either side, so a night starting just before the window still matches.
        val local = dao.observeRange(from - 24 * HOUR_MS, now + HOUR_MS).first()
        val known = local.mapNotNull { it.hcRecordId }.toSet()
        val fresh = records.filter { it.id !in known }
        val manual = local.filter { it.source != SOURCE && it.wakeAt != null }.map { it.id to Span(it.bedAt, it.wakeAt!!) }
        val replace = HealthMerge.manualSleepToReplace(manual, records.map { Span(it.start, it.end) })
        if (fresh.isEmpty() && replace.isEmpty()) return 0
        db.transact {
            fresh.forEach { r ->
                dao.upsert(
                    SleepSessionEntity(
                        id = UuidV7.generate(now), bedAt = r.start, wakeAt = r.end, source = SOURCE, hcRecordId = r.id,
                        sync = stamper.stamp(),
                    ),
                )
            }
            replace.forEach { id ->
                val m = dao.get(id) ?: return@forEach
                if (!m.sync.deleted) dao.upsert(m.copy(notes = REPLACED_NOTE, sync = stamper.stamp(m.sync, deleted = true)))
            }
        }
        listeners.forEach { it.onSleepChanged() }
        return fresh.size
    }

    companion object {
        const val SOURCE = "HEALTH_CONNECT"
        /** Stored on the replaced manual row (not shown in the UI; kept for sync and support). */
        const val REPLACED_NOTE = "Replaced by a Health Connect night"
        const val DAYS = 30
    }
}
