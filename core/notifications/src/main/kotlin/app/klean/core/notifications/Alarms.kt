package app.klean.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Something that arms alarms or re-posts state and must do it again after boot, an app update, a
 * clock or zone change, or a change in the exact-alarm permission. Bind with `@IntoSet`.
 */
fun interface Reschedulable {
    suspend fun reschedule()
}

/** Runs every [Reschedulable] in turn. One failure is logged and never stops the others. */
class Rescheduler @Inject constructor(
    private val all: Set<@JvmSuppressWildcards Reschedulable>,
) {
    suspend fun rescheduleAll() {
        for (r in all) {
            try {
                r.reschedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "reschedule failed: ${r::class.java.simpleName}", e)
            }
        }
    }
}

/**
 * AlarmManager wrapper. Exact (idle-safe) alarms when the user granted them, otherwise the inexact
 * idle-safe call, which is fine for "about every few hours". `USE_EXACT_ALARM` is left for a future
 * alarms module, which is what Play policy allows it for.
 */
class AlarmScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val am: AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun schedule(at: Long, pi: PendingIntent) {
        if (canExact()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                return
            } catch (e: SecurityException) {
                // Revoked between the check and the call; fall through to inexact.
                Log.w("Klean", "exact alarm refused, using inexact", e)
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** For work where a few minutes late doesn't matter (day rollover). */
    fun scheduleInexact(at: Long, pi: PendingIntent) = am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)

    fun cancel(pi: PendingIntent) = am.cancel(pi)
}

/** Declares the set so it is legal before any module contributes one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReschedulableModule {
    @Multibinds abstract fun reschedulables(): Set<Reschedulable>
}
