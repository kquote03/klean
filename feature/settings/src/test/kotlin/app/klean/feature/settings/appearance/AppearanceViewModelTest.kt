package app.klean.feature.settings.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.klean.core.data.prefs.DevicePrefs
import androidx.lifecycle.viewModelScope
import app.klean.core.ui.module.WidgetRefresher
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = DevicePrefs(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(createTempDirectory("prefs").toFile(), "p.preferences_pb") }),
    )
    private val refreshed = AtomicInteger(0)
    private val refresher = WidgetRefresher { refreshed.incrementAndGet() }
    private val vms = mutableListOf<AppearanceViewModel>()

    private fun vm(supported: Boolean) = AppearanceViewModel(prefs, refresher, wallpaperColoursSupported = supported).also { vms += it }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        // Stop VM work before the DataStore's scope goes away, or it leaks into the next test.
        vms.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        scope.cancel()
    }

    private suspend fun <T> real(block: suspend () -> T) = withContext(Dispatchers.Default) { withTimeout(5_000) { block() } }

    @Test fun offByDefaultAndTurningOnSavesAndRepaintsWidgets() = runTest {
        val vm = vm(supported = true)
        val initial = real { vm.state.first { it.loaded } }
        assertFalse(initial.wallpaperColours)
        assertTrue(initial.wallpaperColoursSupported)

        vm.setWallpaperColours(true)
        real { vm.state.first { it.wallpaperColours } }
        assertTrue(real { prefs.dynamicColor.first() })
        // The saved value reaches the state before the widget refresh runs: wait for it.
        real { while (refreshed.get() == 0) delay(10) }
        assertEquals(1, refreshed.get())
    }

    @Test fun belowAndroid12TheOptionIsHidden() = runTest {
        val vm = vm(supported = false)
        assertFalse(real { vm.state.first { it.loaded } }.wallpaperColoursSupported)
    }
}
