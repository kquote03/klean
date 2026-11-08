package app.klean.core.notifications.permissions

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.notifications.AlarmScheduler
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class AndroidPermissionCheckerTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val checker = AndroidPermissionChecker(ctx, AlarmScheduler(ctx))

    @After fun tearDown() = ShadowAlarmManager.reset()

    @Test fun readsEachSystemState() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        shadowOf(ctx.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        shadowOf(ctx as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(ctx.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(ctx.packageName, false)
        val off = checker.current()
        assertFalse(off.notificationsEnabled)
        assertFalse(off.notificationsRuntimeGranted)
        assertFalse(off.exactAlarms)
        assertFalse(off.batteryUnrestricted)
        assertFalse(off.notificationListener)

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(ctx.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(ctx.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(ctx.packageName, true)
        val on = checker.current()
        assertTrue(on.notificationsEnabled)
        assertTrue(on.notificationsRuntimeGranted)
        assertTrue(on.exactAlarms)
        assertTrue(on.batteryUnrestricted)
    }
}
