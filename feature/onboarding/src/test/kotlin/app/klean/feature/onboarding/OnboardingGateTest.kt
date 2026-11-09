package app.klean.feature.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.klean.core.data.prefs.DevicePrefs
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = DevicePrefs(
        PreferenceDataStoreFactory.create(scope = scope) { File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb") },
    )
    private var probes = 0
    private fun gate(hasData: Boolean) = OnboardingGate(prefs, ExistingDataProbe { probes++; hasData })

    @After fun tearDown() = scope.cancel()

    @Test fun freshInstallShowsTheWizard() = runTest {
        assertTrue(gate(hasData = false).shouldShowWizard())
        assertFalse(prefs.onboardingDone.first())
    }

    /** Someone updating from a build without onboarding already has entries; never put a wizard in their way. */
    @Test fun existingInstallWithEntriesSkipsTheWizardAndRemembersIt() = runTest {
        assertFalse(gate(hasData = true).shouldShowWizard())
        assertTrue(prefs.onboardingDone.first())
        // Later starts don't even look at the data again.
        assertFalse(gate(hasData = false).shouldShowWizard())
        assertEquals(1, probes)
    }

    @Test fun markDoneEndsTheWizardForGood() = runTest {
        val g = gate(hasData = false)
        g.markDone()
        assertFalse(g.shouldShowWizard())
        assertEquals(0, probes)
    }
}
