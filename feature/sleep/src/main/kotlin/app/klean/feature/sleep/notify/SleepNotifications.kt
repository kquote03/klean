package app.klean.feature.sleep.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import app.klean.core.common.ApplicationScope
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingKey
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.Reschedulable
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.sleep.R
import app.klean.feature.sleep.data.SleepChangeListener
import app.klean.feature.sleep.data.SleepRepository
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.text.DateFormat as JavaDateFormat
import java.util.Date
import java.util.TimeZone as JavaTimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.serializer

/** Sleep's synced settings (keys live in the module). */
object SleepKeys {
    /** The bedtime reminder: on, at the schedule's `reminder_minutes_before` (30) before bedtime, once. */
    val BedtimeReminder = SettingKey("sleep.bedtimeReminder", true, Boolean.serializer())
    /** A quiet ongoing "Sleeping since 23:40" with "I'm up" while a night is open. */
    val SessionNotification = SettingKey("sleep.sessionNotification", true, Boolean.serializer())
}

/** Pure timing of the bedtime reminder. */
object BedtimePolicy {
    /** The next (bedtime − [leadMinutes]) strictly after [now], in [zone]. */
    fun nextAt(now: Long, bedMinute: Int, leadMinutes: Int, zone: TimeZone): Long {
        val minute = ((bedMinute - leadMinutes) % 1440 + 1440) % 1440
        val today = now.toLocal(zone).date
        val t = atMinute(today, minute, zone)
        return if (t > now) t else atMinute(today.plus(DatePeriod(days = 1)), minute, zone)
    }
}

object SleepChannels {
    const val BEDTIME = "sleep_bedtime"
    const val SESSION = "sleep_session"

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(BEDTIME, ctx.getString(R.string.sleep_channel_bedtime), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    enableVibration(false)
                },
                NotificationChannel(SESSION, ctx.getString(R.string.sleep_channel_session), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            ),
        )
    }
}

internal fun formatTime(ctx: Context, at: Long, zone: TimeZone): String {
    val f = android.text.format.DateFormat.getTimeFormat(ctx) as JavaDateFormat
    f.timeZone = JavaTimeZone.getTimeZone(zone.id)
    return f.format(Date(at))
}

/**
 * The bedtime reminder: (bedtime − 30 min) every day, one gentle notification with "Going to bed", never
 * repeated or escalated. It fires before quiet hours start, so it doesn't consult them; it stays silent when
 * Sleep is off, the reminder is off, this isn't the main phone, or a night is already open.
 */
@Singleton
class BedtimeReminder @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val schedule: SleepScheduleRepository,
    private val repo: dagger.Lazy<SleepRepository>,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val prefs: DevicePrefs,
    private val alarms: AlarmScheduler,
    private val clock: Clock,
    private val zone: ZoneProvider,
) : Reschedulable {
    private fun now() = clock.now().toEpochMilliseconds()

    override suspend fun reschedule() {
        SleepChannels.ensure(ctx)
        val s = schedule.primary()
        if (!gate.isEnabled(ModuleId.SLEEP) || !settings.get(SleepKeys.BedtimeReminder) || s.bedMinutes == s.wakeMinutes) {
            cancelAlarm()
            return
        }
        alarms.schedule(BedtimePolicy.nextAt(now(), s.bedMinutes, s.reminderMinutesBefore, zone.current()), pending(ctx, create = true)!!)
    }

    private fun cancelAlarm() = pending(ctx, create = false)?.let(alarms::cancel)

    fun cancel() {
        cancelAlarm()
        dismiss()
    }

    fun dismiss() = NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)

    /** The alarm (or [force], the debug broadcast). Returns true when the reminder was posted. */
    suspend fun onAlarm(force: Boolean = false): Boolean {
        try {
            if (!gate.isEnabled(ModuleId.SLEEP)) return false
            if (!force && (!settings.get(SleepKeys.BedtimeReminder) || !prefs.isMainPhone.first())) return false
            if (repo.get().open() != null) return false
            return post()
        } finally {
            if (!force) reschedule()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun post(): Boolean {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return false
        SleepChannels.ensure(ctx)
        val s = schedule.primary()
        val bed = BedtimePolicy.nextAt(now(), s.bedMinutes, 0, zone.current())
        val n = NotificationCompat.Builder(ctx, SleepChannels.BEDTIME)
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentTitle(ctx.getString(R.string.sleep_bedtime_title))
            .setContentText(ctx.getString(R.string.sleep_bedtime_body, formatTime(ctx, bed, zone.current())))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openSleep(ctx))
            .addAction(0, ctx.getString(R.string.sleep_go_to_bed), SleepActionReceiver.pending(ctx, SleepActionReceiver.ACTION_BED))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        return true
    }

    companion object {
        const val NOTIFICATION_ID = 5201
        const val REQUEST_CODE = 5201
        const val ACTION_BEDTIME = "app.klean.sleep.BEDTIME"

        fun pending(ctx: Context, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, Intent(ctx, BedtimeReceiver::class.java).setAction(ACTION_BEDTIME),
            PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )

        fun openSleep(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(Intent.ACTION_VIEW, "klean://sleep".toUri()).setPackage(ctx.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * "Sleeping since 23:40" with "I'm up", while a night is open (setting on by default, low importance).
 * Follows every sleep write, and comes back after a reboot.
 */
@Singleton
class SleepSessionNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<SleepRepository>,
    private val settings: SettingsRepository,
    private val gate: ModuleGate,
    private val bedtime: dagger.Lazy<BedtimeReminder>,
    private val zone: ZoneProvider,
) : SleepChangeListener, Reschedulable {
    override suspend fun onSleepChanged() = update()
    override suspend fun reschedule() = update()

    @SuppressLint("MissingPermission")
    suspend fun update() {
        val nm = NotificationManagerCompat.from(ctx)
        val open = repo.get().open()
        if (open != null) bedtime.get().dismiss()
        if (open == null || !gate.isEnabled(ModuleId.SLEEP) || !settings.get(SleepKeys.SessionNotification) || !nm.areNotificationsEnabled()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        SleepChannels.ensure(ctx)
        val n = NotificationCompat.Builder(ctx, SleepChannels.SESSION)
            .setSmallIcon(R.drawable.ic_sleep)
            .setContentTitle(ctx.getString(R.string.sleep_since, formatTime(ctx, open.bedAt, zone.current())))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(BedtimeReminder.openSleep(ctx))
            .addAction(0, ctx.getString(R.string.sleep_im_up), SleepActionReceiver.pending(ctx, SleepActionReceiver.ACTION_UP))
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    fun cancel() = NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)

    companion object {
        const val NOTIFICATION_ID = 5202
    }
}

/** The bedtime alarm. Debug builds also accept `<applicationId>.BEDTIME` (debug manifest): post it now. */
@AndroidEntryPoint
class BedtimeReceiver : BroadcastReceiver() {
    @Inject lateinit var bedtime: BedtimeReminder
    @Inject lateinit var status: StatusRefresh
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val debug = intent.action == context.packageName + ".BEDTIME"
        if (intent.action != BedtimeReminder.ACTION_BEDTIME && !debug) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    bedtime.onAlarm(force = debug)
                    // The Today widget offers "Going to bed" from now on.
                    status.refresh()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "bedtime reminder failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** One tap from a notification: "Going to bed" or "I'm up". */
@AndroidEntryPoint
class SleepActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: SleepRepository
    @Inject lateinit var bedtime: BedtimeReminder
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_BED && action != ACTION_UP) return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    val now = clock.now().toEpochMilliseconds()
                    if (action == ACTION_BED) repo.goToBed(now) else repo.wakeUp(now)
                    bedtime.dismiss()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "sleep action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BED = "app.klean.sleep.GO_TO_BED"
        const val ACTION_UP = "app.klean.sleep.WAKE_UP"

        fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx, if (action == ACTION_BED) 1 else 2, Intent(ctx, SleepActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SleepNotificationsModule {
    @Binds @IntoSet abstract fun bedtime(b: BedtimeReminder): Reschedulable
    @Binds @IntoSet abstract fun sessionReschedule(n: SleepSessionNotifier): Reschedulable
    @Binds @IntoSet abstract fun sessionListener(n: SleepSessionNotifier): SleepChangeListener
}
