package app.klean.core.data.place

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.GeofenceEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.UuidV7
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A place the user named, for "anything from …?" as they leave it (M15). Stored in the `geofence` table. */
data class Place(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val enabled: Boolean,
)

/** What the edit screen saves. Radius is clamped to [MIN_RADIUS_M]..[MAX_RADIUS_M]. */
data class PlaceDraft(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int = DEFAULT_RADIUS_M,
    val enabled: Boolean = true,
) {
    companion object {
        const val MIN_RADIUS_M = 100
        const val MAX_RADIUS_M = 500
        const val DEFAULT_RADIUS_M = 150
    }
}

/** Places live in core/data so later modules (reminders, tasks) can use them too. */
interface PlaceRepository {
    fun observeAll(): Flow<List<Place>>
    suspend fun get(id: String): Place?
    suspend fun enabled(): List<Place>
    /** Every id ever stored, removed ones too, so stale registrations can be cleared. */
    suspend fun allIdsIncludingRemoved(): List<String>
    /** Adds ([id] null) or updates. Returns the id. */
    suspend fun save(id: String?, draft: PlaceDraft): String
    suspend fun setEnabled(id: String, enabled: Boolean)
    /** Tombstones the row; it keeps syncing as deleted. */
    suspend fun delete(id: String)
}

@Singleton
class DefaultPlaceRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
) : PlaceRepository {
    private val dao get() = db.geofenceDao()

    override fun observeAll(): Flow<List<Place>> = dao.observeAll().map { rows -> rows.map { it.toPlace() } }

    override suspend fun get(id: String): Place? = dao.get(id)?.takeIf { !it.sync.deleted }?.toPlace()

    override suspend fun enabled(): List<Place> = dao.enabled().map { it.toPlace() }

    override suspend fun allIdsIncludingRemoved(): List<String> = dao.allIds()

    override suspend fun save(id: String?, draft: PlaceDraft): String = db.transact {
        val prev = id?.let { dao.get(it) }
        val rowId = prev?.id ?: UuidV7.generate(clock.now().toEpochMilliseconds())
        dao.upsert(
            GeofenceEntity(
                id = rowId,
                label = draft.label.trim(),
                latitude = draft.latitude.coerceIn(-90.0, 90.0),
                longitude = draft.longitude.coerceIn(-180.0, 180.0),
                radiusM = draft.radiusM.coerceIn(PlaceDraft.MIN_RADIUS_M, PlaceDraft.MAX_RADIUS_M),
                enabled = draft.enabled,
                sync = stamper.stamp(prev?.sync),
            ),
        )
        rowId
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        db.transact {
            val prev = dao.get(id) ?: return@transact
            dao.upsert(prev.copy(enabled = enabled, sync = stamper.stamp(prev.sync)))
        }
    }

    override suspend fun delete(id: String) {
        db.transact {
            val prev = dao.get(id) ?: return@transact
            dao.upsert(prev.copy(sync = stamper.stamp(prev.sync, deleted = true)))
        }
    }

    private fun GeofenceEntity.toPlace() = Place(id, label, latitude, longitude, radiusM, enabled)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaceModule {
    @Binds abstract fun places(impl: DefaultPlaceRepository): PlaceRepository
}
