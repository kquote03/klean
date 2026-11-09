package app.klean.core.notifications.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.klean.core.notifications.AlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class AndroidPermissionChecker @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val alarms: AlarmScheduler,
) : PermissionChecker {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override fun current(): PermissionStatus {
        val runtime = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return PermissionStatus(
            notificationsEnabled = runtime && NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            notificationsRuntimeGranted = runtime,
            exactAlarms = alarms.canExact(),
            batteryUnrestricted = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
            notificationListener = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx),
            fullScreenIntent = NotificationManagerCompat.from(ctx).canUseFullScreenIntent(),
        )
    }
}

/** System screens that turn each permission on. */
object PermissionIntents {
    /** Null when there is nothing to open (status-only rows). */
    @SuppressLint("BatteryLife") // The exemption is the documented fix for prompts being killed; see PLAN §5.
    fun settingsFor(ctx: Context, kind: PermissionKind): Intent? {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return when (kind) {
            PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            PermissionKind.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            PermissionKind.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            // The row only exists on API 34+, where this screen does.
            PermissionKind.FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            PermissionKind.NOTIFICATION_LISTENER -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Used when the specific screen isn't there (some phones remove it): the app's own info page. */
    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Phone-specific advice about background limits ("auto-start" and similar). */
    fun manufacturerHelpUrl(manufacturer: String = Build.MANUFACTURER): String =
        "https://dontkillmyapp.com/${manufacturer.lowercase()}"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionCheckerModule {
    @Binds abstract fun checker(impl: AndroidPermissionChecker): PermissionChecker
}
