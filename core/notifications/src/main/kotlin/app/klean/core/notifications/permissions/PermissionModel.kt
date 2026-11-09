package app.klean.core.notifications.permissions

import androidx.annotation.StringRes
import app.klean.core.notifications.R

/** The permissions Klean shows on the Permissions screen and in setup, in display order. */
enum class PermissionKind { NOTIFICATIONS, EXACT_ALARMS, BATTERY, FULL_SCREEN, NOTIFICATION_LISTENER }

/**
 * A snapshot of what the system currently allows. Plain booleans so the model is testable on the JVM.
 * Background location (place prompts, M15) joins this when that feature exists; asking for it earlier
 * would be asking for nothing.
 */
data class PermissionStatus(
    /** The app-level notification switch (and, on 33+, the runtime grant) is on. */
    val notificationsEnabled: Boolean,
    /** `POST_NOTIFICATIONS` is granted (always true before API 33). */
    val notificationsRuntimeGranted: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    /** Notification-listener access, shown read-only until the delivery listener exists (M12). */
    val notificationListener: Boolean,
    /** Full-screen intents for the unlock sheet. Android 14+ lets the user refuse them; always true before. */
    val fullScreenIntent: Boolean = true,
)

/** What tapping the row's button does. */
enum class PermissionAction { NONE, REQUEST_RUNTIME, OPEN_SETTINGS }

data class PermissionRowModel(
    val kind: PermissionKind,
    @param:StringRes val titleRes: Int,
    @param:StringRes val whyRes: Int,
    val granted: Boolean,
    val action: PermissionAction,
)

/** Reads the current [PermissionStatus]. An interface so view models can be tested with a fake. */
interface PermissionChecker {
    val sdkInt: Int
    fun current(): PermissionStatus
}

object PermissionModel {
    /**
     * Rows to show for [status] on a device running [sdkInt].
     * [includeReadOnly]: also list status-only rows (the Permissions screen does; setup doesn't).
     * [notificationsAsked]: the runtime dialog was already answered this session, so a second ask
     * would be silently ignored by the system; send the user to the settings screen instead.
     */
    fun rows(
        status: PermissionStatus,
        sdkInt: Int,
        includeReadOnly: Boolean,
        notificationsAsked: Boolean = false,
    ): List<PermissionRowModel> = buildList {
        val notifAction = when {
            status.notificationsEnabled -> PermissionAction.NONE
            sdkInt >= 33 && !status.notificationsRuntimeGranted && !notificationsAsked -> PermissionAction.REQUEST_RUNTIME
            else -> PermissionAction.OPEN_SETTINGS
        }
        add(row(PermissionKind.NOTIFICATIONS, status.notificationsEnabled, notifAction))
        if (sdkInt >= 31) add(row(PermissionKind.EXACT_ALARMS, status.exactAlarms))
        add(row(PermissionKind.BATTERY, status.batteryUnrestricted))
        if (sdkInt >= 34) add(row(PermissionKind.FULL_SCREEN, status.fullScreenIntent))
        if (includeReadOnly) {
            add(row(PermissionKind.NOTIFICATION_LISTENER, status.notificationListener, PermissionAction.NONE))
        }
    }

    private fun row(
        kind: PermissionKind,
        granted: Boolean,
        action: PermissionAction = if (granted) PermissionAction.NONE else PermissionAction.OPEN_SETTINGS,
    ): PermissionRowModel {
        val (title, why) = when (kind) {
            PermissionKind.NOTIFICATIONS -> R.string.permission_notifications_title to R.string.permission_notifications_why
            PermissionKind.EXACT_ALARMS -> R.string.permission_exact_alarms_title to R.string.permission_exact_alarms_why
            PermissionKind.BATTERY -> R.string.permission_battery_title to R.string.permission_battery_why
            PermissionKind.FULL_SCREEN -> R.string.permission_full_screen_title to R.string.permission_full_screen_why
            PermissionKind.NOTIFICATION_LISTENER -> R.string.permission_listener_title to R.string.permission_listener_why
        }
        return PermissionRowModel(kind, title, why, granted, if (granted) PermissionAction.NONE else action)
    }
}
