package app.klean.core.health

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.theme.KleanTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectRowTest {
    @get:Rule val rule = createComposeRule()
    private val sleep = setOf(HealthAccess.READ_SLEEP)

    private fun show(state: HealthRowState, onConnect: () -> Unit = {}) = rule.setContent {
        KleanTheme { HealthConnectRowContent(state, sleep, onConnect = onConnect, onUpdate = {}, onManage = {}) }
    }

    @Test fun missingHealthConnectIsSaidCalmlyWithNothingToTap() {
        show(HealthRowState(HealthAvailability.UNAVAILABLE, emptySet()))
        rule.onNodeWithText("Health Connect isn't on this phone", substring = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }

    @Test fun availableButNotGrantedOffersToConnect() {
        var asked = 0
        show(HealthRowState(HealthAvailability.AVAILABLE, emptySet()), onConnect = { asked++ })
        rule.onNodeWithText("Connect").performClick()
        assertEquals(1, asked)
    }

    @Test fun grantedShowsConnected() {
        show(HealthRowState(HealthAvailability.AVAILABLE, sleep))
        rule.onNodeWithText("Connected", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Manage").assertIsDisplayed()
    }

    @Test fun fakeGatewayReadsNothingWithoutPermissionOrHealthConnect() = runBlocking {
        val g = FakeHealthConnectGateway(grantedAccess = emptySet())
        g.sleep += HcSleep("s", 0, 10, "other")
        assertEquals(emptyList<HcSleep>(), g.readSleep(0, 100))
        g.granted.value = HealthConnectGateway.ALL
        assertEquals(1, g.readSleep(0, 100).size)
        g.available = HealthAvailability.UNAVAILABLE
        assertEquals(emptyList<HcSleep>(), g.readSleep(0, 100))
        assertNull(g.writeExercise("k", 0, 10, null, running = false))
    }
}
