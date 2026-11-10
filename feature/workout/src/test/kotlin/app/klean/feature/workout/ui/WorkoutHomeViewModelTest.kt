package app.klean.feature.workout.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.DubaiZone
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.WorkoutCaptureHandler
import app.klean.feature.workout.data.DefaultWorkoutRepository
import app.klean.feature.workout.data.WorkoutSource
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.tempDevicePrefs
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class FixedTicker(private val clock: FakeClock) : WorkoutTicker(clock) {
    override fun ticks(periodMs: Long): Flow<Long> = flowOf(clock.nowMs)
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WorkoutHomeViewModelTest {
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 10), 18 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val repo = DefaultWorkoutRepository(db, stamper, clock, emptySet())
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        db.close()
    }

    private fun vm() = WorkoutHomeViewModel(repo, FixedTicker(clock), clock, DubaiZone).also { store.put("home", it) }

    @Test fun quickLogShowsInRecentAndUndoRemovesIt() = runBlocking {
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (!s.loaded) s = awaitItem()
            assertTrue(s.recent.isEmpty())
            vm.log("3x15 pushups")
            s = awaitItem()
            while (s.recent.singleOrNull()?.sets?.size != 3) s = awaitItem()
            assertEquals(3, s.recent.single().sets.size)
            vm.undo(s.recent.single().session.id)
            s = awaitItem()
            while (s.recent.isNotEmpty()) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun startingTwiceKeepsOneOpenSession() = runBlocking {
        val vm = vm()
        val ids = mutableListOf<String>()
        vm.start(null) { ids += it }.join()
        vm.start(null) { ids += it }.join()
        assertEquals(ids[0], ids[1])
        vm.state.test {
            var s = awaitItem()
            while (s.open == null) s = awaitItem()
            assertEquals(ids[0], s.open!!.session.id)
            assertTrue(s.recent.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun captureLogsAFinishedSessionAndUndoTombstonesIt() = runBlocking {
        val h = WorkoutCaptureHandler(repo)
        val input = CaptureInput("ran 5k", CaptureSource.QS_TILE, clock.nowMs)
        val id = h.execute(input, CandidateAction(ModuleId.WORKOUT, RuleCaptureClassifier.LOG_WORKOUT, 0.9f))
        val s = repo.session(id)!!
        assertEquals(WorkoutSource.CAPTURE, s.session.source)
        assertEquals(5000, s.sets.single().distanceM)
        h.undo(id)
        assertNull(repo.session(id))
    }
}
