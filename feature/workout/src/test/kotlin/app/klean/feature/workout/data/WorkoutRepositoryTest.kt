package app.klean.feature.workout.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import app.klean.feature.workout.tempDevicePrefs
import app.klean.shared.time.atMinute
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
class WorkoutRepositoryTest {
    private val day = LocalDate(2026, 11, 10)
    private val clock = FakeClock(atMinute(day, 18 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private var changes = 0
    private val repo = DefaultWorkoutRepository(db, stamper, clock, setOf(WorkoutChangeListener { changes++ }))

    @After fun close() = db.close()

    @Test fun templatesParseTheirTextAndCreateExercises() = runBlocking {
        val id = repo.saveTemplate(null, "Morning", "3x15 pushups, 1 min plank")
        val t = repo.template(id)!!
        assertEquals("Morning", t.name)
        assertEquals(2, WorkoutItem.decode(t.items).size)
        assertEquals(listOf("plank", "pushups"), repo.observeExercises().first().map { it.name })
        repo.saveTemplate(id, "", "20 Pushups")
        assertEquals("Pushups", repo.template(id)!!.name)
        // "Pushups" is the same exercise as "pushups": no duplicate.
        assertEquals(2, repo.observeExercises().first().size)
        repo.deleteTemplate(id)
        assertNull(repo.template(id))
        assertTrue(repo.observeTemplates().first().isEmpty())
    }

    @Test fun quickStartRunsOneSessionAtATime() = runBlocking {
        val a = repo.start()
        val b = repo.start()
        assertEquals(a, b)
        assertNotNull(repo.openSession())
        repo.addSet(a, WorkoutItem("pushups", WorkoutKind.STRENGTH, reps = 12))
        repo.logItems(a, listOf(WorkoutItem("squats", WorkoutKind.STRENGTH, sets = 3, reps = 10)))
        clock.nowMs += 25 * 60_000
        repo.finish(a)
        assertNull(repo.openSession())
        val s = repo.session(a)!!
        assertEquals(listOf("pushups", "squats", "squats", "squats"), s.sets.map { it.exercise })
        assertEquals(25 * 60_000L, s.session.endedAt!! - s.session.startedAt)
        assertTrue(s.sets.all { it.exerciseId != null })
    }

    @Test fun setsCanBeEditedAndRemoved() = runBlocking {
        val s = repo.start()
        val set = repo.addSet(s, WorkoutItem("bench", WorkoutKind.STRENGTH, reps = 8))
        repo.updateSet(set, reps = 10, weightKg = 40.0, durationS = null, distanceM = null)
        assertEquals(10, repo.session(s)!!.sets.single().reps)
        assertEquals(40.0, repo.session(s)!!.sets.single().weightKg!!, 0.0)
        repo.deleteSet(set)
        assertTrue(repo.session(s)!!.sets.isEmpty())
    }

    @Test fun freeformLogMakesAFinishedSession() = runBlocking {
        val id = repo.logText("did 30 pushups", clock.nowMs, WorkoutSource.CAPTURE)
        val s = repo.session(id)!!
        assertEquals(clock.nowMs, s.session.endedAt)
        assertEquals(WorkoutSource.CAPTURE, s.session.source)
        assertEquals(30, s.sets.single().reps)
        // Words that don't parse are still logged, as the note.
        val gym = repo.session(repo.logText("climbing with Sam", clock.nowMs))!!
        assertEquals("climbing with Sam", gym.session.notes)
        assertTrue(gym.sets.isEmpty())
        // A timed log starts before it ends.
        val swim = repo.session(repo.logText("20 min swim", clock.nowMs))!!
        assertEquals(20 * 60_000L, swim.session.endedAt!! - swim.session.startedAt)
    }

    @Test fun deleteTombstonesTheSessionAndRestoreBringsItBack() = runBlocking {
        val id = repo.logText("3x10 squats", clock.nowMs)
        repo.delete(id)
        assertNull(repo.session(id))
        assertTrue(repo.observeSessions().first().isEmpty())
        assertTrue(db.workoutSetDao().forSession(id).isEmpty())
        repo.restore(id)
        assertEquals(3, repo.session(id)!!.sets.size)
    }

    @Test fun historyListsEverySetOfAnExerciseNewestFirst() = runBlocking {
        repo.logText("20 pushups", clock.nowMs)
        clock.nowMs += 86_400_000
        repo.logText("3x15 pushups", clock.nowMs)
        val pushups = repo.observeExercises().first().single { it.name == "pushups" }
        val h = repo.observeHistory(pushups.id).first()
        assertEquals(listOf(15, 15, 15, 20), h.map { it.set.reps })
        assertTrue(h.first().startedAt > h.last().startedAt)
        assertEquals(clock.nowMs, repo.lastSessionStart())
        assertTrue(changes > 0)
    }
}
