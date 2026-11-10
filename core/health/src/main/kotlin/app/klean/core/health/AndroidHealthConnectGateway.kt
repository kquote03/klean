package app.klean.core.health

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The real gateway over `androidx.health.connect:connect-client`. Every call is wrapped so a failure reads as "nothing". */
@Singleton
class AndroidHealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectGateway {
    private val _granted = MutableStateFlow<Set<HealthAccess>>(emptySet())
    override val granted: StateFlow<Set<HealthAccess>> = _granted.asStateFlow()
    override val ownPackage: String = context.packageName

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthAvailability.AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null
    }

    override fun availability(): HealthAvailability = runCatching {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
            else -> HealthAvailability.UNAVAILABLE
        }
    }.getOrDefault(HealthAvailability.UNAVAILABLE)

    override suspend fun refresh() {
        val c = client ?: run { _granted.value = emptySet(); return }
        val perms = runCatching { c.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        _granted.value = HealthAccess.entries.filter { permission(it) in perms }.toSet()
    }

    override fun permissionsFor(access: Set<HealthAccess>): Set<String> = access.map(::permission).toSet()

    override suspend fun readSleep(from: Long, to: Long): List<HcSleep> {
        val c = client?.takeIf { canUse(HealthAccess.READ_SLEEP) } ?: return emptyList()
        return safely("readSleep") {
            c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(from, to))).records.map {
                HcSleep(it.metadata.id, it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.metadata.dataOrigin.packageName)
            }
        } ?: emptyList()
    }

    override suspend fun readExercise(from: Long, to: Long): List<HcExercise> {
        val c = client?.takeIf { canUse(HealthAccess.READ_EXERCISE) } ?: return emptyList()
        return safely("readExercise") {
            c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(from, to))).records.map {
                HcExercise(
                    id = it.metadata.id, start = it.startTime.toEpochMilli(), end = it.endTime.toEpochMilli(),
                    title = it.title, origin = it.metadata.dataOrigin.packageName,
                    running = it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                )
            }
        } ?: emptyList()
    }

    override suspend fun writeExercise(clientId: String, start: Long, end: Long, title: String?, running: Boolean): String? {
        val c = client?.takeIf { canUse(HealthAccess.WRITE_EXERCISE) } ?: return null
        return safely("writeExercise") {
            val zone = ZoneId.systemDefault().rules
            val s = Instant.ofEpochMilli(start)
            val e = Instant.ofEpochMilli(maxOf(end, start + 1000))
            val record = ExerciseSessionRecord(
                startTime = s,
                startZoneOffset = zone.getOffset(s),
                endTime = e,
                endZoneOffset = zone.getOffset(e),
                metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE), clientRecordId = clientId),
                exerciseType = if (running) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING else ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                title = title,
            )
            c.insertRecords(listOf(record)).recordIdsList.firstOrNull()
        }
    }

    private fun range(from: Long, to: Long) = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))

    private inline fun <T> safely(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "$what failed: ${e.javaClass.simpleName}")
        null
    }

    companion object {
        private const val TAG = "KleanHealth"

        fun permission(access: HealthAccess): String = when (access) {
            HealthAccess.READ_SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
            HealthAccess.READ_EXERCISE -> HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            HealthAccess.WRITE_EXERCISE -> HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        }

        /** Health Connect is part of the system from Android 14; before that it is a separate app. */
        val isBuiltIn: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {
    @Binds abstract fun gateway(g: AndroidHealthConnectGateway): HealthConnectGateway
}
