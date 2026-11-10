package app.klean.feature.sleep.data

import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.SleepSessionEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sleep.QuietHoursSource
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.UuidV7
import app.klean.shared.time.HOUR_MS
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object SleepSource {
    const val MANUAL = "MANUAL"
    const val CAPTURE = "CAPTURE"
}

/** Told after every sleep write (outside the transaction): status line, widget, Today, the notifications. */
fun interface SleepChangeListener {
    suspend fun onSleepChanged()
}

interface SleepRepository {
    /** Live sessions, newest bedtime first (open one included). */
    fun observeSessions(): Flow<List<SleepSessionEntity>>
    fun observeOpen(): Flow<SleepSessionEntity?>
    suspend fun open(): SleepSessionEntity?
    suspend fun get(id: String): SleepSessionEntity?

    /**
     * Opens a session at [at]. A session already open is kept (no-op) unless it is older than
     * [STALE_MS], which means "I'm up" never came: that one is closed first.
     */
    suspend fun goToBed(at: Long, source: String = SleepSource.MANUAL): String
    /**
     * Closes the open session at [at]. With none open, logs a night from the scheduled bedtime before [at]
     * (for "woke up at 7" with no "going to bed"). Returns the session id.
     */
    suspend fun wakeUp(at: Long, source: String = SleepSource.MANUAL): String
    /** "Going to bed" when nothing is open, otherwise "I'm up". */
    suspend fun toggle(at: Long): Toggle
    /** A whole night at once (capture, "Add a night"). */
    suspend fun log(bedAt: Long, wakeAt: Long, source: String = SleepSource.MANUAL): String
    suspend fun update(id: String, bedAt: Long, wakeAt: Long?)
    suspend fun delete(id: String)
    suspend fun restore(id: String)

    data class Toggle(val id: String, val asleep: Boolean)

    companion object {
        const val STALE_MS = 20 * HOUR_MS
        /** A forgotten open session is closed at most this long after bedtime. */
        const val MAX_NIGHT_MS = 12 * HOUR_MS
    }
}

@Singleton
class DefaultSleepRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val schedule: QuietHoursSource,
    private val zone: ZoneProvider,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards SleepChangeListener>,
) : SleepRepository {
    private val dao get() = db.sleepDao()
    private fun now() = clock.now().toEpochMilliseconds()

    override fun observeSessions(): Flow<List<SleepSessionEntity>> = dao.observeAll()
    override fun observeOpen(): Flow<SleepSessionEntity?> = dao.observeAll().map { l -> l.firstOrNull { it.wakeAt == null } }.distinctUntilChanged()
    override suspend fun open(): SleepSessionEntity? = dao.open()
    override suspend fun get(id: String): SleepSessionEntity? = dao.get(id)?.takeUnless { it.sync.deleted }

    override suspend fun goToBed(at: Long, source: String): String {
        val id = db.transact {
            val open = dao.open()
            if (open != null) {
                if (at - open.bedAt <= SleepRepository.STALE_MS) return@transact open.id
                dao.upsert(open.copy(wakeAt = minOf(at, open.bedAt + SleepRepository.MAX_NIGHT_MS), sync = stamper.stamp(open.sync)))
            }
            insert(at, null, source)
        }
        notifyChanged()
        return id
    }

    override suspend fun wakeUp(at: Long, source: String): String {
        val open = dao.open()
        if (open == null) {
            val q = schedule.current()
            val bed = SleepMath.latestAtOrBefore(at, listOf(q.startMinute), zone.current(), strict = true)
            return log(bed, at, source)
        }
        db.transact {
            val o = dao.get(open.id)!!
            dao.upsert(o.copy(wakeAt = at.coerceAtLeast(o.bedAt), sync = stamper.stamp(o.sync)))
        }
        notifyChanged()
        return open.id
    }

    override suspend fun toggle(at: Long): SleepRepository.Toggle {
        val open = dao.open()
        return if (open == null || at - open.bedAt > SleepRepository.STALE_MS) {
            SleepRepository.Toggle(goToBed(at), asleep = true)
        } else {
            SleepRepository.Toggle(wakeUp(at), asleep = false)
        }
    }

    override suspend fun log(bedAt: Long, wakeAt: Long, source: String): String {
        val id = db.transact { insert(minOf(bedAt, wakeAt), maxOf(bedAt, wakeAt), source) }
        notifyChanged()
        return id
    }

    private suspend fun insert(bedAt: Long, wakeAt: Long?, source: String): String {
        val id = UuidV7.generate(now())
        dao.upsert(SleepSessionEntity(id, bedAt = bedAt, wakeAt = wakeAt, source = source, sync = stamper.stamp()))
        return id
    }

    override suspend fun update(id: String, bedAt: Long, wakeAt: Long?) {
        db.transact {
            val s = dao.get(id)?.takeUnless { it.sync.deleted } ?: return@transact
            dao.upsert(s.copy(bedAt = bedAt, wakeAt = wakeAt?.coerceAtLeast(bedAt), sync = stamper.stamp(s.sync)))
        }
        notifyChanged()
    }

    override suspend fun delete(id: String) = setDeleted(id, true)
    override suspend fun restore(id: String) = setDeleted(id, false)

    private suspend fun setDeleted(id: String, deleted: Boolean) {
        db.transact {
            val s = dao.get(id) ?: return@transact
            if (s.sync.deleted != deleted) dao.upsert(s.copy(sync = stamper.stamp(s.sync, deleted = deleted)))
        }
        notifyChanged()
    }

    private suspend fun notifyChanged() = listeners.forEach { it.onSleepChanged() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepDataModule {
    @Binds abstract fun repo(impl: DefaultSleepRepository): SleepRepository
    @Multibinds abstract fun listeners(): Set<SleepChangeListener>
}
