package app.klean.feature.workout.jog

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.klean.feature.workout.R
import app.klean.shared.health.GeoPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Location fixes while a run is on. */
interface LocationFeed {
    fun fixes(): Flow<GeoPoint>
}

/**
 * `LocationManager` only (no Play services): GPS when the phone has it, else the platform's fused provider
 * (Android 12+), else the network provider. One fix a second.
 */
class AndroidLocationFeed @Inject constructor(@ApplicationContext private val ctx: Context) : LocationFeed {
    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<GeoPoint> = callbackFlow {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val providers = lm.allProviders
        val provider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val listener = LocationListener { l: Location -> trySend(GeoPoint(l.latitude, l.longitude, l.time, if (l.hasAccuracy()) l.accuracy else 0f)) }
        runCatching { lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper()) }
            .onFailure { close(it) }
        awaitClose { lm.removeUpdates(listener) }
    }
}

/**
 * The foreground service of a GPS run (type `location`; foreground only, no background location). Started from
 * the Workout screen after the location permission is granted; its notification shows the distance and has
 * a Finish action.
 */
@AndroidEntryPoint
class JogTrackingService : Service() {
    @Inject lateinit var tracker: JogTracker
    @Inject lateinit var feed: LocationFeed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FINISH -> {
                scope.launch {
                    tracker.finish()
                    stop()
                }
                return START_NOT_STICKY
            }
            ACTION_DISCARD -> {
                tracker.discard()
                stop()
                return START_NOT_STICKY
            }
        }
        if (!hasLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0.0), type)
        if (!started) {
            started = true
            tracker.start()
            scope.launch { feed.fixes().collect { tracker.onFix(it) } }
            scope.launch {
                // Update the notification at most every 10 m of progress.
                tracker.state.map { (it.distanceM / 10).toInt() }.distinctUntilChanged().collectLatest { tenths ->
                    if (tracker.state.value.running) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(tenths * 10.0))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(distanceM: Double): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(Intent.ACTION_VIEW, Uri.parse("klean://workout")).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val finish = PendingIntent.getService(
            this, 1, Intent(this, JogTrackingService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle(getString(R.string.workout_run_notification_title))
            .setContentText(formatKm(this, distanceM))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.workout_run_finish), finish)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    companion object {
        const val CHANNEL = "workout_run"
        const val NOTIFICATION_ID = 5102
        const val ACTION_FINISH = "app.klean.workout.RUN_FINISH"
        const val ACTION_DISCARD = "app.klean.workout.RUN_DISCARD"

        fun hasLocation(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, JogTrackingService::class.java))

        fun finish(ctx: Context) = ctx.startService(Intent(ctx, JogTrackingService::class.java).setAction(ACTION_FINISH))

        fun discard(ctx: Context) = ctx.startService(Intent(ctx, JogTrackingService::class.java).setAction(ACTION_DISCARD))

        fun ensureChannel(ctx: Context) {
            val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.workout_channel_run), NotificationManager.IMPORTANCE_LOW)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** "1.24 km" (two decimals under 10 km). */
        fun formatKm(ctx: Context, m: Double): String =
            ctx.getString(R.string.workout_run_km, String.format(Locale.getDefault(), if (m < 10_000) "%.2f" else "%.1f", m / 1000))
    }
}
