package app.klean.feature.diet.delivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.diet.DeliveryMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Task 12.2: the delivery apps editor writes the synced lists. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppsViewModelTest {
    private val clock = FakeClock(1_000L)
    private val db = inMemoryDb()
    private val settings = SettingsRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    private val vms = ViewModelStore()
    private fun <T : ViewModel> T.tracked(): T = also { vms.put("vm-${System.nanoTime()}", it) }

    @After fun tearDown() {
        // Cancel VM collectors while Main is still the test dispatcher and the database is open;
        // otherwise a late emission resumes on the real (absent) Main and fails a later test.
        vms.clear()
        Dispatchers.resetMain()
        db.close()
    }

    @Test fun startsWithTheBuiltInApps() = runBlocking {
        val vm = DeliveryAppsViewModel(settings).tracked()
        assertEquals(DeliveryMatcher.DEFAULT_APPS, vm.uiState.first { it.loaded }.packages)
    }

    @Test fun addsAndRemovesPackages() = runBlocking {
        val vm = DeliveryAppsViewModel(settings).tracked()
        assertTrue(vm.addPackage("  com.example.food ").await())
        assertFalse(vm.addPackage("not a package").await())
        assertFalse(vm.addPackage("com.talabat").await()) // already there
        vm.removePackage("com.careem.acma").join()
        assertEquals(
            listOf("com.talabat", "com.deliveroo.orderapp", "com.noon.buyerapp", "com.example.food"),
            settings.get(SyncedKeys.DietDeliveryApps),
        )
    }

    @Test fun addsAndRemovesMatchWords() = runBlocking {
        val vm = DeliveryAppsViewModel(settings).tracked()
        assertTrue(vm.addWord(" rider dropped ").await())
        assertFalse(vm.addWord("   ").await())
        assertFalse(vm.addWord("Rider dropped").await())
        assertTrue(vm.addWord("at the door").await())
        vm.removeWord("rider dropped").join()
        assertEquals(listOf("at the door"), settings.get(SyncedKeys.DietDeliveryMatchWords))
        assertEquals(listOf("at the door"), vm.uiState.first { it.words.size == 1 }.words)
    }
}
