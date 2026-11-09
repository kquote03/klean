package app.klean.feature.diet.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.place.DefaultPlaceRepository
import app.klean.core.data.place.PlaceDraft
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.R
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.tempDevicePrefs
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Task 15.1: adding, editing and deleting a place, with or without a location fix. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlaceEditViewModelTest {
    private val clock = FakeClock(EngineRig.t(13))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val gate = SettingsModuleGate(SettingsRepository(db, stamper))
    private val places = DefaultPlaceRepository(db, stamper, clock)
    private val backend = FakeGeofenceBackend()
    private val registrar = GeofenceRegistrar(places, gate, backend)
    private var fix: LatLng? = LatLng(25.197197, 55.279638)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun vm(id: String? = null) = PlaceEditViewModel(SavedStateHandle(mapOf("placeId" to id)), places, registrar) { fix }

    @Test fun addWithCurrentLocationSavesAndWatchesIt() = runBlocking {
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onLabel("Food court")
        vm.useCurrentLocation().join()
        assertEquals("25.197197", vm.state.value.latitude)
        assertEquals("55.279638", vm.state.value.longitude)
        vm.onRadius(230)
        vm.save().join()
        assertTrue(vm.state.value.done)
        val p = places.observeAll().first().single()
        assertEquals("Food court", p.label)
        assertEquals(25.197197, p.latitude, 1e-9)
        assertEquals(230, p.radiusM)
        assertEquals(listOf(p.id), backend.registered.keys.toList())
    }

    @Test fun noFixExplainsAndTypedCoordinatesStillWork() = runBlocking {
        fix = null
        val vm = vm()
        vm.state.first { it.loaded }
        vm.useCurrentLocation().join()
        assertEquals(R.string.places_location_unavailable, vm.state.value.note)
        vm.onLabel("Office")
        vm.onLatitude("25,2048")
        vm.onLongitude("55.2708")
        assertTrue(vm.state.value.canSave)
        vm.save().join()
        assertEquals(25.2048, places.observeAll().first().single().latitude, 1e-9)
    }

    @Test fun cannotSaveWithoutANameOrWithImpossibleCoordinates() = runBlocking {
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onLatitude("95")
        vm.onLongitude("55")
        assertFalse(vm.state.value.canSave)
        vm.onLatitude("25")
        assertFalse("no name yet", vm.state.value.canSave)
        vm.save().join()
        assertEquals(R.string.places_needs_name_and_spot, vm.state.value.note)
        assertTrue(places.observeAll().first().isEmpty())
    }

    @Test fun editLoadsTheRowAndDeleteTombstonesAndStopsWatching() = runBlocking {
        val id = places.save(null, PlaceDraft("Gym", 25.1, 55.1, 300))
        registrar.reschedule()
        val vm = vm(id)
        val s = vm.state.first { it.loaded }
        assertFalse(s.isNew)
        assertEquals("Gym", s.label)
        assertEquals(300, s.radiusM)
        vm.delete().join()
        assertNull(places.get(id))
        assertTrue(db.geofenceDao().get(id)!!.sync.deleted)
        assertTrue(backend.registered.isEmpty())
    }
}
