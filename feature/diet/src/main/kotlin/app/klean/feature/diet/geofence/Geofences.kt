package app.klean.feature.diet.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import app.klean.core.common.ApplicationScope
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.place.Place
import app.klean.core.data.place.PlaceRepository
import app.klean.core.notifications.QuietHoursService
import app.klean.core.notifications.Reschedulable
import app.klean.feature.diet.notification.PromptPhrasings
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.prompt.PromptPresenter
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.id.UuidV7
import app.klean.shared.module.ModuleId
import app.klean.shared.time.MINUTE_MS
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Watches places for the system. An interface so registration logic is tested without LocationManager. */
interface GeofenceBackend {
    /** Location permission is granted, so registering can work at all. */
    fun canMonitor(): Boolean
    fun register(place: Place)
    fun unregister(id: String)
}

/**
 * AOSP proximity alerts (plan §2: no Google Play services, so it works on any Android, including
 * de-Googled phones and Waydroid). The system adds `KEY_PROXIMITY_ENTERING` to the PendingIntent, which
 * is why it must be mutable. One PendingIntent per place, told apart by its data URI.
 */
class ProximityAlertBackend @Inject constructor(@ApplicationContext private val ctx: Context) : GeofenceBackend {
    private val lm: LocationManager? get() = ctx.getSystemService(LocationManager::class.java)

    override fun canMonitor(): Boolean = hasFineLocation(ctx)

    @SuppressLint("MissingPermission") // canMonitor() is checked by the registrar first
    override fun register(place: Place) {
        try {
            lm?.addProximityAlert(place.latitude, place.longitude, place.radiusM.toFloat(), -1, intent(ctx, place.id, create = true)!!)
        } catch (e: SecurityException) {
            Log.w("Klean", "proximity alert refused", e)
        } catch (e: IllegalArgumentException) {
            Log.w("Klean", "proximity alert rejected", e) // e.g. no location provider on this device
        }
    }

    override fun unregister(id: String) {
        val pi = intent(ctx, id, create = false) ?: return
        runCatching { lm?.removeProximityAlert(pi) }
        pi.cancel()
    }

    companion object {
        fun intent(ctx: Context, placeId: String, create: Boolean): PendingIntent? {
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val flags = mutable or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
            return PendingIntent.getBroadcast(
                ctx, REQUEST_CODE,
                Intent(ctx, GeofenceExitReceiver::class.java)
                    .setData(Uri.parse("klean://place/$placeId"))
                    .putExtra(GeofenceExitReceiver.EXTRA_PLACE_ID, placeId),
                flags,
            )
        }

        const val REQUEST_CODE = 31

        fun hasFineLocation(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Needed on Android 10+ for alerts while Klean isn't on screen. */
        fun hasBackgroundLocation(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Keeps the system's watch list equal to the enabled places. Runs after boot and app updates (via
 * [Reschedulable]), after every place edit, after a permission change, and when diet is switched on
 * or off: with diet off, or without location permission, nothing is watched.
 */
@Singleton
class GeofenceRegistrar @Inject constructor(
    private val places: PlaceRepository,
    private val gate: ModuleGate,
    private val backend: GeofenceBackend,
) : Reschedulable {
    private val mutex = Mutex()

    override suspend fun reschedule() = mutex.withLock {
        val active = if (gate.isEnabled(ModuleId.DIET) && backend.canMonitor()) places.enabled() else emptyList()
        val activeIds = active.map { it.id }.toSet()
        places.allIdsIncludingRemoved().filter { it !in activeIds }.forEach(backend::unregister)
        active.forEach(backend::register)
    }
}

/**
 * "Anything from Dubai Mall?" as the user leaves a place (Task 15.2). Stores only that it happened:
 * a `prompt_log` row with trigger GEOFENCE and the place id, never a location. Once per place per hour.
 */
@Singleton
class GeofenceExitHandler @Inject constructor(
    private val db: KleanDatabase,
    private val places: PlaceRepository,
    private val writer: PromptLogWriter,
    private val gate: ModuleGate,
    private val quiet: QuietHoursService,
    private val clock: Clock,
    private val presenter: PromptPresenter,
) {
    private val mutex = Mutex()

    /** Returns whether the user was asked. */
    suspend fun onTransition(placeId: String, entering: Boolean): Boolean = mutex.withLock {
        if (entering) return@withLock false
        val now = clock.now().toEpochMilliseconds()
        if (!gate.isEnabled(ModuleId.DIET)) return@withLock false
        val place = places.get(placeId)?.takeIf { it.enabled } ?: return@withLock false
        if (quiet.isQuiet(now)) return@withLock false
        val last = db.promptLogDao().lastForTrigger(PromptTrigger.GEOFENCE, placeId)
        if (last != null && now - last.sentAt < DEBOUNCE_MS) return@withLock false
        val id = UuidV7.generate(now)
        writer.insert { sync ->
            PromptLogEntity(
                id = id, moduleId = ModuleId.DIET.value, trigger = PromptTrigger.GEOFENCE, triggerDetail = placeId,
                format = PromptFormat.STANDARD, phrasingId = PromptPhrasings.GEOFENCE_ID, scheduledFor = now, sentAt = now,
                outcome = PromptOutcome.PENDING, outcomeAt = null, sync = sync,
            )
        }
        presenter.present(id, PromptFormat.STANDARD, PromptPhrasings.GEOFENCE_ID, PromptTrigger.GEOFENCE, place.label)
        true
    }

    companion object {
        const val DEBOUNCE_MS = 60 * MINUTE_MS
    }
}

/** Proximity alerts land here. Only exits matter. */
@AndroidEntryPoint
class GeofenceExitReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: GeofenceExitHandler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_PLACE_ID) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true)
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { handler.onTransition(id, entering) }
            } catch (e: Exception) {
                Log.w("Klean", "place prompt failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PLACE_ID = "place_id"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GeofenceModule {
    @Binds abstract fun backend(impl: ProximityAlertBackend): GeofenceBackend
    @Binds @IntoSet abstract fun reschedulable(r: GeofenceRegistrar): Reschedulable
}
