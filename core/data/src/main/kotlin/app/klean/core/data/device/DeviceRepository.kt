package app.klean.core.data.device

import android.os.Build
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.DeviceEntity
import app.klean.core.data.db.transact
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.sync.SyncStamper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The `device` table: one synced row per install, so every device knows which phone owns alarms. */
interface DeviceRepository {
    /** Upserts this install's row (name, main-phone flag, last seen). Called at every app start. */
    suspend fun registerThisDevice()

    /** Sets [DevicePrefs.isMainPhone] and mirrors it into this install's row. */
    suspend fun setMainPhone(v: Boolean)

    fun observeAll(): Flow<List<DeviceEntity>>
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceName

@Singleton
class DefaultDeviceRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val prefs: DevicePrefs,
    private val clock: Clock,
    @DeviceName private val deviceName: String,
) : DeviceRepository {
    private val dao get() = db.deviceDao()

    override suspend fun registerThisDevice() {
        val id = prefs.deviceId()
        val main = prefs.isMainPhone.first()
        db.transact {
            val prev = dao.get(id)
            dao.upsert(
                DeviceEntity(
                    id = id,
                    name = deviceName,
                    platform = ANDROID,
                    isMainPhone = main,
                    lastSeenAt = clock.now().toEpochMilliseconds(),
                    sync = stamper.stamp(prev?.sync, deleted = false),
                ),
            )
        }
    }

    override suspend fun setMainPhone(v: Boolean) {
        prefs.setIsMainPhone(v)
        registerThisDevice()
    }

    override fun observeAll(): Flow<List<DeviceEntity>> = dao.observeAll()

    companion object {
        const val ANDROID = "ANDROID"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {
    @Binds abstract fun devices(impl: DefaultDeviceRepository): DeviceRepository

    companion object {
        @Provides @DeviceName
        fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() && it != "unknown" }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android" }
    }
}
