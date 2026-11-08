package app.klean.feature.settings.permissions

import app.klean.core.notifications.permissions.PermissionAction
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.notifications.permissions.PermissionKind
import app.klean.core.notifications.permissions.PermissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePermissionChecker(
    var status: PermissionStatus = PermissionStatus(false, false, false, false, false),
    override val sdkInt: Int = 33,
) : PermissionChecker {
    var reads = 0
    override fun current(): PermissionStatus { reads++; return status }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun refreshPicksUpChangesMadeInSystemSettings() {
        val checker = FakePermissionChecker()
        val vm = PermissionsViewModel(checker, includeReadOnly = true)
        assertTrue(vm.rows.value.none { it.granted })

        // The user comes back from the exact-alarm screen with it turned on.
        checker.status = checker.status.copy(exactAlarms = true)
        vm.refresh()
        val exact = vm.rows.value.single { it.kind == PermissionKind.EXACT_ALARMS }
        assertTrue(exact.granted)
        assertEquals(PermissionAction.NONE, exact.action)
    }

    @Test fun deniedRuntimeRequestSwitchesToTheSettingsScreen() {
        val vm = PermissionsViewModel(FakePermissionChecker(), includeReadOnly = false)
        assertEquals(PermissionAction.REQUEST_RUNTIME, vm.rows.value.first().action)
        vm.onNotificationsResult(granted = false)
        assertEquals(PermissionAction.OPEN_SETTINGS, vm.rows.value.first().action)
    }

    @Test fun grantedRuntimeRequestShowsOn() {
        val checker = FakePermissionChecker()
        val vm = PermissionsViewModel(checker, includeReadOnly = false)
        checker.status = checker.status.copy(notificationsEnabled = true, notificationsRuntimeGranted = true)
        vm.onNotificationsResult(granted = true)
        assertTrue(vm.rows.value.first().granted)
    }

    @Test fun setupHidesTheListenerRow() {
        val vm = PermissionsViewModel(FakePermissionChecker(), includeReadOnly = false)
        assertFalse(vm.rows.value.any { it.kind == PermissionKind.NOTIFICATION_LISTENER })
        val settings = PermissionsViewModel(FakePermissionChecker(), includeReadOnly = true)
        assertTrue(settings.rows.value.any { it.kind == PermissionKind.NOTIFICATION_LISTENER })
    }
}
