package app.klean.core.notifications.permissions

import app.klean.core.notifications.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionModelTest {
    private val allOff = PermissionStatus(
        notificationsEnabled = false,
        notificationsRuntimeGranted = false,
        exactAlarms = false,
        batteryUnrestricted = false,
        notificationListener = false,
    )

    @Test fun api33ShowsEveryRowInOrderWithExplanations() {
        val rows = PermissionModel.rows(allOff, sdkInt = 33, includeReadOnly = true)
        assertEquals(
            listOf(PermissionKind.NOTIFICATIONS, PermissionKind.EXACT_ALARMS, PermissionKind.BATTERY, PermissionKind.NOTIFICATION_LISTENER),
            rows.map { it.kind },
        )
        rows.forEach { assertFalse(it.granted) }
        assertEquals(
            listOf(R.string.permission_notifications_why, R.string.permission_exact_alarms_why, R.string.permission_battery_why, R.string.permission_listener_why),
            rows.map { it.whyRes },
        )
    }

    @Test fun onboardingLeavesOutTheReadOnlyListenerRow() {
        val rows = PermissionModel.rows(allOff, sdkInt = 33, includeReadOnly = false)
        assertFalse(rows.any { it.kind == PermissionKind.NOTIFICATION_LISTENER })
    }

    @Test fun listenerIsStatusOnlyWithNoAction() {
        val row = PermissionModel.rows(allOff, 33, includeReadOnly = true).single { it.kind == PermissionKind.NOTIFICATION_LISTENER }
        assertEquals(PermissionAction.NONE, row.action)
        val on = PermissionModel.rows(allOff.copy(notificationListener = true), 33, true).single { it.kind == PermissionKind.NOTIFICATION_LISTENER }
        assertTrue(on.granted)
    }

    @Test fun exactAlarmRowOnlyFromApi31() {
        assertFalse(PermissionModel.rows(allOff, 30, true).any { it.kind == PermissionKind.EXACT_ALARMS })
        val row = PermissionModel.rows(allOff, 31, true).single { it.kind == PermissionKind.EXACT_ALARMS }
        assertEquals(PermissionAction.OPEN_SETTINGS, row.action)
    }

    @Test fun notificationsAskAtRuntimeOnApi33UntilAskedOnce() {
        val first = PermissionModel.rows(allOff, 33, true).first()
        assertEquals(PermissionAction.REQUEST_RUNTIME, first.action)
        // Once the system dialog was answered "no", asking again does nothing; open the settings screen instead.
        val after = PermissionModel.rows(allOff, 33, true, notificationsAsked = true).first()
        assertEquals(PermissionAction.OPEN_SETTINGS, after.action)
    }

    @Test fun notificationsOpenSettingsBeforeApi33OrWhenOnlyTheAppSwitchIsOff() {
        assertEquals(PermissionAction.OPEN_SETTINGS, PermissionModel.rows(allOff, 32, true).first().action)
        val runtimeOk = allOff.copy(notificationsRuntimeGranted = true)
        assertEquals(PermissionAction.OPEN_SETTINGS, PermissionModel.rows(runtimeOk, 33, true).first().action)
    }

    @Test fun grantedRowsHaveNoAction() {
        val allOn = PermissionStatus(true, true, true, true, true)
        PermissionModel.rows(allOn, 34, true).forEach {
            assertTrue(it.granted)
            assertEquals(PermissionAction.NONE, it.action)
        }
    }

    @Test fun batteryRowOffersSettings() {
        val row = PermissionModel.rows(allOff, 28, true).single { it.kind == PermissionKind.BATTERY }
        assertEquals(PermissionAction.OPEN_SETTINGS, row.action)
        assertEquals(R.string.permission_battery_title, row.titleRes)
    }
}
