package app.klean.feature.reminders.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.klean.core.common.ApplicationScope
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.feature.reminders.AlarmRinger
import app.klean.feature.reminders.RINGING
import app.klean.feature.reminders.ReminderFormatter
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.feature.reminders.notify.ReminderChannels
import app.klean.feature.reminders.notify.ReminderNotifier
import app.klean.shared.diet.prompt.PromptFormat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Rings an alarm until it's handled: the default alarm sound on the alarm stream (USAGE_ALARM), looped,
 * with a repeating vibration, as a foreground service of type `mediaPlayback` whose notification opens
 * [AlarmActivity] full screen. After [AUTO_STOP_MS] it goes quiet and leaves a silent notification.
 *
 * Why a `mediaPlayback` service: it's playing audio the user asked for, needs no special permission
 * beyond FOREGROUND_SERVICE_MEDIA_PLAYBACK, and may start from the background because it starts while
 * an exact alarm (`setAlarmClock`) is being delivered. `systemExempted` would need USE_EXACT_ALARM,
 * which Play reserves for alarm-clock apps. If the start is refused anyway (no exact-alarm access),
 * [ServiceAlarmRinger] returns false and the caller posts a looping notification instead.
 */
@AndroidEntryPoint
class AlarmRingService : Service() {
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var fmt: ReminderFormatter
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var state: AlarmRingState
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var current: ReminderEntity? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_STOP -> if (id == null || id == current?.id) stopRinging(leaveNote = false)
            ACTION_RING -> {
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val fireAt = intent.getLongExtra(EXTRA_FIRE_AT, System.currentTimeMillis())
                if (id != null) start(ReminderEntity(id = id, title = title, fireAt = fireAt, zone = "UTC", isAlarm = true, state = RINGING, sync = PLACEHOLDER_SYNC))
            }
            else -> if (current == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(r: ReminderEntity) {
        // A newer alarm replaces one still ringing; the older one stays as a quiet note.
        current?.takeIf { it.id != r.id }?.let { old -> stopSound(); notifier.ping(old, PromptFormat.STANDARD, quiet = true) }
        current = r
        ReminderChannels.ensure(this)
        val n = AndroidReminderNotifier.alarmNotification(this, fmt, r)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, FOREGROUND_ID, n, type)
        startSound()
        state.ringing.value = r.id
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopRinging(leaveNote = true) }, AUTO_STOP_MS)
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = runCatching {
            RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }.onFailure { Log.w("Klean", "alarm sound failed", it) }.getOrNull()
        vibrator = (if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java))
            ?.takeIf { it.hasVibrator() }
            ?.also { v ->
                runCatching {
                    v.vibrate(
                        VibrationEffect.createWaveform(ReminderChannels.ALARM_VIBRATION, 0),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                }
            }
    }

    private fun stopSound() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    /** Stops the sound and the service. [leaveNote]: the 10 minutes ran out, so a quiet notification stays. */
    private fun stopRinging(leaveNote: Boolean) {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        val r = current
        current = null
        state.ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (leaveNote && r != null) {
            scope.launch {
                // The real row (its actual time), if it's still unhandled.
                val row = db.reminderDao().get(r.id)?.takeIf { it.state == RINGING && !it.sync.deleted } ?: return@launch
                notifier.ping(row, PromptFormat.STANDARD, quiet = true)
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        state.ringing.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "app.klean.reminders.RING"
        const val ACTION_STOP = "app.klean.reminders.STOP_RING"
        const val EXTRA_ID = "reminderId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FIRE_AT = "fireAt"
        const val FOREGROUND_ID = 3002
        const val AUTO_STOP_MS = 10 * 60_000L
        private val PLACEHOLDER_SYNC = app.klean.core.data.db.entity.SyncColumns(0, 0, "", false, 0)
    }
}

/** Which alarm is ringing right now (null = none). The alarm screen closes itself when it goes quiet. */
@Singleton
class AlarmRingState @Inject constructor() {
    val ringing = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> get() = ringing
}

class ServiceAlarmRinger @Inject constructor(@ApplicationContext private val ctx: Context) : AlarmRinger {
    override fun ring(r: ReminderEntity): Boolean = try {
        ContextCompat.startForegroundService(
            ctx,
            Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_RING)
                .putExtra(AlarmRingService.EXTRA_ID, r.id)
                .putExtra(AlarmRingService.EXTRA_TITLE, r.title)
                .putExtra(AlarmRingService.EXTRA_FIRE_AT, r.fireAt),
        )
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException (API 31+) or a background-start refusal.
        Log.w("Klean", "alarm service refused; using a looping notification", e)
        false
    }

    override fun stop(id: String) {
        try {
            ctx.startService(Intent(ctx, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_STOP).putExtra(AlarmRingService.EXTRA_ID, id))
        } catch (e: Exception) {
            // Not running, and can't be started from the background just to stop: nothing to do.
            Log.d("Klean", "alarm stop: service not running")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmRingerModule {
    @Binds abstract fun ringer(impl: ServiceAlarmRinger): AlarmRinger
}
