package app.klean.watch

import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.WorkoutSessionEntity
import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.core.data.db.entity.WorkoutTemplateEntity
import app.klean.feature.workout.data.SessionWithSets
import app.klean.shared.wear.ActiveWorkout
import app.klean.shared.wear.NextUp
import app.klean.shared.wear.WorkoutRef
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchSnapshotsTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private val today = LocalDate(2026, 11, 11)
    private fun at(h: Int, m: Int = 0) = LocalDateTime(2026, 11, 11, h, m).toInstant(zone).toEpochMilliseconds()
    private val now = at(12)
    private val sync = SyncColumns(0, 0, "", false, 0)

    private fun task(id: String, due: String? = "2026-11-11", minutes: Int? = null, done: Boolean = false, status: String = "OPEN", parent: String? = null, deleted: Boolean = false) =
        TaskEntity(id, "inbox", "Task $id", dueDate = due, dueMinutes = minutes, completedAt = if (done) 1 else null, status = if (done) "DONE" else status, parentId = parent, sync = sync.copy(deleted = deleted))

    private fun reminder(id: String, fireAt: Long, state: String = "SCHEDULED", snoozed: Long? = null) =
        ReminderEntity(id, title = "Reminder $id", fireAt = fireAt, zone = "Asia/Dubai", state = state, snoozedUntil = snoozed, sync = sync)

    @Test fun theSoonestTimedThingComesFirst() {
        val next = WatchSnapshots.nextUp(
            tasks = listOf(task("a", minutes = 15 * 60), task("b")),
            reminders = listOf(reminder("r", at(14))),
            today = today, nowMs = now, zone = zone,
        )
        assertEquals(NextUp("REMINDER", "Reminder r", at(14)), next)
    }

    @Test fun aSnoozedReminderCountsAtItsSnoozeTime() {
        val next = WatchSnapshots.nextUp(
            tasks = listOf(task("a", minutes = 13 * 60)),
            reminders = listOf(reminder("r", at(11), state = "SNOOZED", snoozed = at(12, 30))),
            today = today, nowMs = now, zone = zone,
        )
        assertEquals(NextUp("REMINDER", "Reminder r", at(12, 30)), next)
    }

    @Test fun anUntimedTaskForTodayIsTheFallback() {
        val next = WatchSnapshots.nextUp(
            tasks = listOf(
                task("past", minutes = 9 * 60), // its time today has gone
                task("done", done = true), task("sub", parent = "x"), task("gone", deleted = true),
                task("someday", status = "SOMEDAY"), task("tomorrow", due = "2026-11-12", minutes = 9 * 60),
                task("today"),
            ),
            reminders = emptyList(), today = today, nowMs = now, zone = zone,
        )
        assertEquals(NextUp("TASK", "Task today", null), next)
    }

    @Test fun nothingLeftIsNull() {
        assertNull(WatchSnapshots.nextUp(listOf(task("done", done = true)), emptyList(), today, now, zone))
    }

    @Test fun workoutsAreInTheirOrderAndAnOpenSessionShowsItsSets() {
        val templates = listOf(
            WorkoutTemplateEntity("t2", "Arms", "[]", position = 1, sync = sync),
            WorkoutTemplateEntity("t1", "Legs", "[]", position = 0, sync = sync),
            WorkoutTemplateEntity("t3", "Old", "[]", position = 2, sync = sync.copy(deleted = true)),
        )
        assertEquals(listOf(WorkoutRef("t1", "Legs"), WorkoutRef("t2", "Arms")), WatchSnapshots.workouts(templates))
        val session = SessionWithSets(
            WorkoutSessionEntity("s1", templateId = "t1", startedAt = now, sync = sync),
            listOf(set("a"), set("b")),
        )
        assertEquals(ActiveWorkout("s1", "Legs", 2), WatchSnapshots.active(session, templates, fallbackName = "Workout"))
        assertEquals(ActiveWorkout("s1", "Workout", 2), WatchSnapshots.active(session.copy(session = session.session.copy(templateId = null)), templates, "Workout"))
        assertNull(WatchSnapshots.active(session.copy(session = session.session.copy(endedAt = now)), templates, "Workout"))
        assertNull(WatchSnapshots.active(null, templates, "Workout"))
    }

    private fun set(id: String) = WorkoutSetEntity(id, sessionId = "s1", exercise = "Squat", kind = "STRENGTH", position = 0, reps = 10, sync = sync)
}
