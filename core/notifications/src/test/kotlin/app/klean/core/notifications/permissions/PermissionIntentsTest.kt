package app.klean.core.notifications.permissions

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionIntentsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun eachSettingsRowOpensTheRightSystemScreen() {
        val exact = PermissionIntents.settingsFor(ctx, PermissionKind.EXACT_ALARMS)!!
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, exact.action)
        assertEquals("package:${ctx.packageName}", exact.dataString)

        val battery = PermissionIntents.settingsFor(ctx, PermissionKind.BATTERY)!!
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery.action)
        assertEquals("package:${ctx.packageName}", battery.dataString)

        val notifications = PermissionIntents.settingsFor(ctx, PermissionKind.NOTIFICATIONS)!!
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifications.action)
        assertEquals(ctx.packageName, notifications.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val fullScreen = PermissionIntents.settingsFor(ctx, PermissionKind.FULL_SCREEN)!!
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, fullScreen.action)
        assertEquals("package:${ctx.packageName}", fullScreen.dataString)
    }

    @Test fun listenerHasNoScreenYet() {
        assertNull(PermissionIntents.settingsFor(ctx, PermissionKind.NOTIFICATION_LISTENER))
    }

    @Test fun manufacturerHelpLinkIsLowercase() {
        assertEquals("https://dontkillmyapp.com/samsung", PermissionIntents.manufacturerHelpUrl("Samsung"))
    }
}
