package app.klean.feature.diet.geofence

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.place.DefaultPlaceRepository
import app.klean.core.data.place.Place
import app.klean.core.data.place.PlaceDraft
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.QuietHoursService
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.notification.PromptPhrasings
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.prompt.FakePresenter
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.module.ModuleId
import app.klean.shared.time.MINUTE_MS
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class FakeGeofenceBackend(var permitted: Boolean = true) : GeofenceBackend {
    val registered = linkedMapOf<String, Place>()
    val unregistered = mutableListOf<String>()
    override fun canMonitor() = permitted
    override fun register(place: Place) { registered[place.id] = place }
    override fun unregister(id: String) {
        registered.remove(id)
        unregistered += id
    }
}

/** Task 15.2 decision logic. Nov 9 2026 Asia/Dubai, quiet 00:00–08:00 by default. */
@RunWith(AndroidJUnit4::class)
class GeofenceTest {
    private val clock = FakeClock(EngineRig.t(13))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val places = DefaultPlaceRepository(db, stamper, clock)
    private val presenter = FakePresenter()
    private val backend = FakeGeofenceBackend()
    private val exits = GeofenceExitHandler(
        db, places, PromptLogWriter(db, stamper, clock), gate, QuietHoursService(settings, DubaiZone, clock), clock, presenter,
    )
    private val registrar = GeofenceRegistrar(places, gate, backend)

    @After fun tearDown() = db.close()

    private suspend fun mall() = places.save(null, PlaceDraft("Dubai Mall", 25.1972, 55.2796, 150))

    @Test fun leavingAPlaceAsksAboutItAndLogsOnlyThePlaceId() = runBlocking {
        val id = mall()
        assertTrue(exits.onTransition(id, entering = false))
        val row = db.promptLogDao().recent(10).single()
        assertEquals(PromptTrigger.GEOFENCE, row.trigger)
        assertEquals(id, row.triggerDetail)
        assertEquals(PromptOutcome.PENDING, row.outcome)
        assertEquals(ModuleId.DIET.value, row.moduleId)
        val shown = presenter.shown.single()
        assertEquals(row.id, shown.logId)
        assertEquals(PromptTrigger.GEOFENCE, shown.trigger)
        assertEquals(PromptPhrasings.GEOFENCE_ID, shown.phrasingId)
        assertEquals("Dubai Mall", shown.placeLabel)
    }

    @Test fun arrivingAsksNothing() = runBlocking {
        assertFalse(exits.onTransition(mall(), entering = true))
        assertTrue(presenter.shown.isEmpty())
    }

    @Test fun oncePerPlacePerHour() = runBlocking {
        val a = mall()
        val b = places.save(null, PlaceDraft("Office", 25.2, 55.3, 200))
        assertTrue(exits.onTransition(a, entering = false))
        clock.nowMs += 59 * MINUTE_MS
        assertFalse(exits.onTransition(a, entering = false))
        assertTrue("another place is its own debounce", exits.onTransition(b, entering = false))
        clock.nowMs += 1 * MINUTE_MS
        assertTrue(exits.onTransition(a, entering = false))
        assertEquals(3, presenter.shown.size)
    }

    @Test fun quietHoursAskNothing() = runBlocking {
        val id = mall()
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(12 * 60, 14 * 60))
        assertFalse(exits.onTransition(id, entering = false))
        assertTrue(db.promptLogDao().recent(10).isEmpty())
    }

    @Test fun dietOffOrPlaceOffOrRemovedAsksNothing() = runBlocking {
        val id = mall()
        gate.setEnabled(ModuleId.DIET, false)
        assertFalse(exits.onTransition(id, entering = false))
        gate.setEnabled(ModuleId.DIET, true)
        places.setEnabled(id, false)
        assertFalse(exits.onTransition(id, entering = false))
        places.setEnabled(id, true)
        places.delete(id)
        assertFalse(exits.onTransition(id, entering = false))
        assertFalse(exits.onTransition("unknown", entering = false))
        assertTrue(presenter.shown.isEmpty())
    }

    @Test fun registersEnabledPlacesAndClearsTheRest() = runBlocking {
        val a = mall()
        val b = places.save(null, PlaceDraft("Gym", 25.0, 55.0, 100, enabled = false))
        val c = places.save(null, PlaceDraft("Office", 25.2, 55.3, 200))
        places.delete(c)
        registrar.reschedule()
        assertEquals(listOf(a), backend.registered.keys.toList())
        assertTrue(backend.unregistered.containsAll(listOf(b, c)))
    }

    @Test fun dietOffUnregistersEverything() = runBlocking {
        val a = mall()
        registrar.reschedule()
        assertEquals(listOf(a), backend.registered.keys.toList())
        gate.setEnabled(ModuleId.DIET, false)
        registrar.reschedule()
        assertTrue(backend.registered.isEmpty())
        assertEquals(listOf(a), backend.unregistered)
    }

    @Test fun withoutLocationPermissionNothingIsRegistered() = runBlocking {
        mall()
        backend.permitted = false
        registrar.reschedule()
        assertTrue(backend.registered.isEmpty())
    }
}
