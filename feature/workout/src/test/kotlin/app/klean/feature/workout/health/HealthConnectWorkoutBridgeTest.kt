package app.klean.feature.workout.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.core.health.FakeHealthConnectGateway
import app.klean.core.health.HcExercise
import app.klean.core.health.HealthAvailability
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.data.DefaultWorkoutRepository
import app.klean.feature.workout.data.WorkoutChangeListener
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.jog.JogTracker
import app.klean.feature.workout.jog.RouteStore
import app.klean.feature.workout.tempDevicePrefs
import app.klean.shared.health.GeoPoint
import app.klean.shared.time.atMinute
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectWorkoutBridgeTest {
    private val nov10 = LocalDate(2026, 11, 10)
    private fun at(h: Int, m: Int = 0, s: Int = 0) = atMinute(nov10, h * 60 + m, Dubai) + s * 1000L
    private val clock = FakeClock(at(18))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private var changes = 0
    private val listeners = setOf(WorkoutChangeListener { changes++ })
    private val repo = DefaultWorkoutRepository(db, stamper, clock, listeners)
    private val hc = FakeHealthConnectGateway(ownPackage = "app.klean.debug")
    private val bridge = HealthConnectWorkoutBridge(hc, db, stamper, clock, listeners)
    private val routesDir = createTempDirectory("routes").toFile()
    private val routes = object : RouteStore(ApplicationProvider.getApplicationContext<Context>()) {
        override val dir: File get() = routesDir
    }

    @After fun close() = db.close()

    @Test fun finishedSessionIsWrittenOnceAndLinked() = runBlocking {
        clock.nowMs = at(17)
        val id = repo.start()
        clock.nowMs = at(17, 40)
        repo.finish(id)
        bridge.writeSession(id)
        bridge.writeSession(id) // a second write updates the same record
        assertEquals(1, hc.exercise.size)
        val s = db.workoutSessionDao().get(id)!!
        assertEquals(hc.exercise.single().id, s.hcRecordId)
        assertEquals(at(17), hc.exercise.single().start)
    }

    @Test fun nothingIsWrittenWithoutAccessOrHealthConnect() = runBlocking {
        val id = repo.logText("30 pushups", at(17))
        hc.granted.value = emptySet()
        bridge.writeSession(id)
        hc.granted.value = app.klean.core.health.HealthConnectGateway.ALL
        hc.available = HealthAvailability.UNAVAILABLE
        bridge.writeSession(id)
        assertTrue(hc.exercise.isEmpty())
        assertNull(db.workoutSessionDao().get(id)!!.hcRecordId)
        assertEquals(0, bridge.importRecent(30))
    }

    @Test fun otherAppsSessionsComeInOnceAndOwnRecordsAreSkipped() = runBlocking {
        hc.exercise += HcExercise("yoga", at(7), at(7, 45), "Morning yoga", "com.other")
        hc.exercise += HcExercise("mine", at(9), at(9, 30), null, "app.klean.debug")
        assertEquals(1, bridge.importRecent(30))
        assertEquals(0, bridge.importRecent(30))
        val all = db.workoutSessionDao().observeAll().first()
        assertEquals(listOf("yoga"), all.map { it.hcRecordId })
        assertEquals("HEALTH_CONNECT", all.single().source)
        assertEquals("Morning yoga", all.single().notes)
    }

    /** A Klean GPS run and the watch's record of the same run: Klean's (with the route) stays, linked. */
    @Test fun watchRunOverlappingAKleanRunIsLinkedNotDuplicated() = runBlocking {
        val tracker = JogTracker(clock, repo, routes, bridge)
        hc.granted.value = setOf(app.klean.core.health.HealthAccess.READ_EXERCISE) // no write: keep the watch record the only one
        clock.nowMs = at(6)
        tracker.start()
        (0..30).forEach { i -> tracker.onFix(GeoPoint(25.2 + i * 0.000899322 / 3, 55.27, at(6, 0, i * 20), 5f)) }
        clock.nowMs = at(6, 10)
        val runId = tracker.finish()!!
        hc.exercise += HcExercise("watch-run", at(6, 0, 30), at(6, 10, 30), "Run", "com.watch", running = true)

        assertEquals(0, bridge.importRecent(30))
        val all = db.workoutSessionDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(runId, all.single().id)
        assertEquals("watch-run", all.single().hcRecordId)
        assertNotNull(all.single().routeBlob)
    }

    @Test fun joggingRecordsDistanceRouteAndARunSet() = runBlocking {
        val tracker = JogTracker(clock, repo, routes, bridge)
        clock.nowMs = at(6)
        tracker.start()
        // 1 km north in 100 m steps every 30 s, with one poor fix and one jump thrown in.
        (0..10).forEach { i -> tracker.onFix(GeoPoint(25.2 + i * 0.000899322, 55.27, at(6, 0, i * 30), 5f)) }
        tracker.onFix(GeoPoint(25.3, 55.27, at(6, 5, 1), 300f))
        tracker.onFix(GeoPoint(25.4, 55.27, at(6, 5, 2), 5f))
        assertTrue(abs(tracker.state.value.distanceM - 1000) < 10)
        clock.nowMs = at(6, 6)
        val id = tracker.finish()!!
        assertEquals(false, tracker.state.value.running)
        val s = repo.session(id)!!
        assertTrue(abs(s.session.distanceM!! - 1000) <= 10)
        assertEquals("GPS", s.session.source)
        assertEquals(11, routes.read(s.session.routeBlob!!).size)
        assertEquals("Run", s.sets.single().exercise)
        assertEquals(360, s.sets.single().durationS)
        // And it went to Health Connect as a run.
        assertTrue(hc.exercise.single().running)
    }
}
