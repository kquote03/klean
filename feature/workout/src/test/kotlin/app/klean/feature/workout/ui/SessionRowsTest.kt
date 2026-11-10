package app.klean.feature.workout.ui

import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRowsTest {
    private val sync = SyncColumns(0, 0, "d", false, 0)
    private fun set(pos: Int, name: String, reps: Int? = null, kg: Double? = null, dur: Int? = null, dist: Int? = null) =
        WorkoutSetEntity("s$pos", "x", name, WorkoutKind.STRENGTH.name, pos, reps, kg, dur, dist, sync = sync)

    @Test fun plannedFirstThenExtrasInOrder() {
        val planned = listOf(WorkoutItem("pushups", WorkoutKind.STRENGTH, 3, 15), WorkoutItem("plank", WorkoutKind.TIMED, durationS = 60))
        val rows = SessionRows.build(planned, listOf(set(2, "lunges", 10), set(0, "Pushups", 15), set(1, "pushups", 12), set(3, "dips", 8)))
        assertEquals(listOf("pushups", "plank", "lunges", "dips"), rows.map { it.exercise })
        assertEquals(2, rows[0].done.size)
        assertEquals(WorkoutItem("pushups", WorkoutKind.STRENGTH, 1, 15), rows[0].next)
        // An unplanned exercise repeats its last set.
        assertEquals(10, rows[2].next.reps)
        assertEquals(0, rows[1].done.size)
    }

    @Test fun labels() {
        assertEquals("15", SessionRows.setLabel(set(0, "a", 15)))
        assertEquals("5 × 60 kg", SessionRows.setLabel(set(0, "a", 5, 60.0)))
        assertEquals("1 min", SessionRows.setLabel(set(0, "a", dur = 60)))
        assertEquals("2.5 km", SessionRows.setLabel(set(0, "a", dist = 2500)))
    }

    @Test fun countsAndMinutes() {
        val s = SessionWithSets(WorkoutSessionEntity("x", startedAt = 0, endedAt = 25 * 60_000L, sync = sync), listOf(set(0, "a", 1), set(1, "A", 1), set(2, "b", 1)))
        assertEquals(2, SessionRows.itemCount(s))
        assertEquals(25, SessionRows.minutes(s))
        assertNull(SessionRows.minutes(s.copy(session = s.session.copy(endedAt = null))))
    }
}
