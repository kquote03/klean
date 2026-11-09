package app.klean.feature.settings.appearance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.ui.module.WidgetRefresher
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var refreshed = 0
    private val refresher = WidgetRefresher { refreshed++ }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    private suspend fun <T> real(block: suspend () -> T) = withContext(Dispatchers.Default) { withTimeout(5_000) { block() } }

    @Test fun offByDefaultAndTurningOnSavesAndRepaintsWidgets() = runTest {
        val vm = AppearanceViewModel(prefs, refresher, wallpaperColoursSupported = true)
        val initial = real { vm.state.first { it.loaded } }
        assertFalse(initial.wallpaperColours)
        assertTrue(initial.wallpaperColoursSupported)

        vm.setWallpaperColours(true)
        real { vm.state.first { it.wallpaperColours } }
        assertTrue(real { prefs.dynamicColor.first() })
        assertEquals(1, refreshed)
    }

    @Test fun belowAndroid12TheOptionIsHidden() = runTest {
        val vm = AppearanceViewModel(prefs, refresher, wallpaperColoursSupported = false)
        assertFalse(real { vm.state.first { it.loaded } }.wallpaperColoursSupported)
    }
}
