package app.klean.feature.workout.health

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.core.health.HealthAccess
import app.klean.core.health.HealthConnectGateway
import app.klean.core.health.canUse
import app.klean.feature.workout.HealthConnectBridge
import app.klean.feature.workout.data.WorkoutChangeListener
import app.klean.shared.health.HealthMerge
import app.klean.shared.health.Span
import app.klean.shared.health.WorkoutSpan
import app.klean.shared.id.UuidV7
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Workouts and Health Connect (M22):
 * - a finished Klean session is written there once (its id is the client record id, so a rewrite updates);
 * - other apps' sessions of the last 30 days come in as `HEALTH_CONNECT` sessions;
 * - a watch run overlapping a Klean session by more than 70% is the same workout: one is kept (the one with a
 *   GPS route; Klean's when neither has one) and the other is only linked through `hc_record_id`.
 */
@Singleton
class HealthConnectWorkoutBridge @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards WorkoutChangeListener>,
) : HealthConnectBridge {
    private val sessions get() = db.workoutSessionDao()
    private fun now() = clock.now().toEpochMilliseconds()

    override suspend fun writeSession(sessionId: String) {
        gateway.refresh()
        if (!gateway.canUse(HealthAccess.WRITE_EXERCISE)) return
        val s = sessions.get(sessionId)?.takeUnless { it.sync.deleted } ?: return
        val end = s.endedAt ?: return
        if (s.source == SOURCE) return // came from Health Connect; never echo it back
        val title = s.notes ?: if (s.routeBlob != null) RUN_TITLE else null
        val id = gateway.writeExercise(s.id, s.startedAt, end, title, running = s.routeBlob != null) ?: return
        if (s.hcRecordId == id) return
        db.transact {
            val fresh = sessions.get(sessionId) ?: return@transact
            sessions.upsert(fresh.copy(hcRecordId = id, sync = stamper.stamp(fresh.sync)))
        }
    }

    override suspend fun importRecent(days: Int): Int {
        gateway.refresh()
        if (!gateway.canUse(HealthAccess.READ_EXERCISE)) return 0
        val now = now()
        val from = now - days * DAY_MS
        val records = gateway.readExercise(from, now).filter { it.origin != gateway.ownPackage && it.end > it.start }
        if (records.isEmpty()) return 0
        val local = sessions.observeRange(from - DAY_MS, now + DAY_MS).first()
        val known = local.mapNotNull { it.hcRecordId }.toSet()
        val toInsert = mutableListOf<app.klean.core.health.HcExercise>()
        val toLink = mutableMapOf<String, String>() // local session id -> hc id
        for (r in records) {
            if (r.id in known) continue
            val hcSpan = WorkoutSpan(r.id, Span(r.start, r.end), hasRoute = false)
            val twin = local.firstOrNull { l ->
                l.endedAt != null && l.hcRecordId == null && l.id !in toLink &&
                    HealthMerge.duplicate(WorkoutSpan(l.id, Span(l.startedAt, l.endedAt!!), l.routeBlob != null), hcSpan) != null
            }
            if (twin != null) {
                toLink[twin.id] = r.id
            } else {
                toInsert += r
            }
        }
        if (toInsert.isEmpty() && toLink.isEmpty()) return 0
        db.transact {
            toInsert.forEach { r ->
                sessions.upsert(
                    WorkoutSessionEntity(
                        id = UuidV7.generate(now), startedAt = r.start, endedAt = r.end, notes = r.title, source = SOURCE,
                        hcRecordId = r.id, sync = stamper.stamp(),
                    ),
                )
            }
            toLink.forEach { (localId, hcId) ->
                val l = sessions.get(localId) ?: return@forEach
                sessions.upsert(l.copy(hcRecordId = hcId, sync = stamper.stamp(l.sync)))
            }
        }
        listeners.forEach { it.onWorkoutsChanged() }
        return toInsert.size
    }

    companion object {
        const val SOURCE = "HEALTH_CONNECT"
        const val DAYS = 30
        /** Health Connect's title for a GPS run recorded in Klean (not shown in Klean). */
        const val RUN_TITLE = "Run"
        private const val DAY_MS = 24 * 3_600_000L
    }
}
