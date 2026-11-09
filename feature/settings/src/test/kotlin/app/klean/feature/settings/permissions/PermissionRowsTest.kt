package app.klean.feature.settings.permissions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import app.klean.core.notifications.permissions.PermissionKind
import app.klean.core.notifications.permissions.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionRowsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsNeutralStatusAndATurnOnButtonOnlyWhereSomethingCanBeDone() {
        val checker = FakePermissionChecker(PermissionStatus(true, true, false, false, false))
        val vm = PermissionsViewModel(checker, includeReadOnly = true)
        val clicked = mutableListOf<PermissionKind>()
        compose.setContent { KleanTheme { PermissionRowsContent(vm.rows.value, onAction = { clicked += it.kind }, onHelp = {}) } }

        compose.onNodeWithText("Notifications").assertExists()
        compose.onNodeWithText("So check-ins arrive on time instead of hours late.").assertExists()
        compose.onAllNodesWithText("On").assertCountEquals(1)
        compose.onAllNodesWithText("Off").assertCountEquals(4)
        // Exact alarms, battery and location (M15) can be turned on; the listener row is status only.
        compose.onAllNodesWithText("Turn on").assertCountEquals(3)
        compose.onAllNodesWithText("Turn on")[0].performClick()
        assertEquals(listOf(PermissionKind.EXACT_ALARMS), clicked)
        compose.onNodeWithText("Tips for this phone").assertExists()
    }
}
