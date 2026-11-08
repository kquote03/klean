package app.klean.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val scheduler = AlarmScheduler(ctx)
    private val pi = PendingIntent.getBroadcast(ctx, 1, Intent("test.ALARM").setPackage(ctx.packageName), PendingIntent.FLAG_IMMUTABLE)

    @After fun tearDown() = ShadowAlarmManager.reset()

    @Test fun exactWhenAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(scheduler.canExact())
        scheduler.schedule(1_000_000L, pi)
        val a = shadowOf(am).peekNextScheduledAlarm()!!
        assertEquals(1_000_000L, a.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, a.type)
        assertTrue(a.isAllowWhileIdle)
        assertNull(a.windowLengthMs.takeIf { it > 0 })
    }

    @Test fun inexactFallbackWhenExactIsDenied() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(scheduler.canExact())
        scheduler.schedule(2_000_000L, pi)
        val a = shadowOf(am).peekNextScheduledAlarm()!!
        assertEquals(2_000_000L, a.triggerAtMs)
        assertTrue(a.isAllowWhileIdle)
    }

    @Test fun cancelRemovesTheAlarm() {
        scheduler.schedule(3_000_000L, pi)
        scheduler.cancel(pi)
        assertNull(shadowOf(am).peekNextScheduledAlarm())
    }

    @Test fun inexactAlwaysUsesTheWindowlessIdleSafeCall() {
        scheduler.scheduleInexact(4_000_000L, pi)
        val a = shadowOf(am).peekNextScheduledAlarm()!!
        assertEquals(4_000_000L, a.triggerAtMs)
        assertTrue(a.isAllowWhileIdle)
    }
}
