package app.klean.core.data.reminder

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.FakeClock
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.inMemoryDb
import app.klean.core.data.sync.SyncStamper
import app.klean.core.data.tempDevicePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultReminderRepositoryTest {
    private val clock = FakeClock(1_762_700_000_000L)
    private val db = inMemoryDb()
    private val changed = mutableListOf<String>()
    private val listener = ReminderChangeListener { changed += it }
    private val repo = DefaultReminderRepository(db, SyncStamper(db, tempDevicePrefs(clock), clock), clock, setOf(listener))

    @After fun close() = db.close()

    @Test fun addStampsSyncColumnsAndTellsListeners() = runBlocking {
        val fireAt = clock.nowMs + 3_600_000
        val id = repo.add("call Ahmed", fireAt, "Asia/Dubai", isAlarm = true)
        val row = db.reminderDao().get(id)!!
        assertEquals('7', id[14]) // UUIDv7
        assertEquals("call Ahmed", row.title)
        assertEquals(fireAt, row.fireAt)
        assertEquals("Asia/Dubai", row.zone)
        assertTrue(row.isAlarm)
        assertEquals("SCHEDULED", row.state)
        assertEquals(clock.nowMs, row.sync.createdAt)
        assertTrue(row.sync.changeSeq > 0)
        assertFalse(row.sync.deleted)
        assertEquals(listOf(id), changed)
    }

    @Test fun updateRestampsAndTellsListeners() = runBlocking {
        val id = repo.add("stretch", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false)
        val before = db.reminderDao().get(id)!!
        clock.nowMs += 1_000
        repo.update(before.copy(state = "SNOOZED", snoozedUntil = clock.nowMs + 600_000))
        val after = db.reminderDao().get(id)!!
        assertEquals("SNOOZED", after.state)
        assertEquals(before.sync.createdAt, after.sync.createdAt)
        assertEquals(clock.nowMs, after.sync.updatedAt)
        assertTrue(after.sync.changeSeq > before.sync.changeSeq)
        assertEquals(listOf(id, id), changed)
    }

    @Test fun deleteTombstones() = runBlocking {
        val id = repo.add("stretch", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false)
        repo.delete(id)
        val row = db.reminderDao().get(id)!!
        assertTrue("tombstone, never a hard delete", row.sync.deleted)
        assertTrue(repo.observeUpcoming(0).first().isEmpty())
        assertEquals(listOf(id, id), changed)
    }

    @Test fun observeForTaskFilters() = runBlocking {
        val a = repo.add("a", clock.nowMs + 1, "Asia/Dubai", isAlarm = false, taskId = "task-1")
        repo.add("b", clock.nowMs + 2, "Asia/Dubai", isAlarm = false, taskId = "task-2")
        repo.add("c", clock.nowMs + 3, "Asia/Dubai", isAlarm = false)
        val gone = repo.add("d", clock.nowMs + 4, "Asia/Dubai", isAlarm = false, taskId = "task-1")
        repo.delete(gone)
        assertEquals(listOf(a), repo.observeForTask("task-1").first().map { it.id })
    }

    @Test fun observeUpcomingSkipsPastAndDone() = runBlocking {
        val past = repo.add("past", clock.nowMs - 60_000, "Asia/Dubai", isAlarm = false)
        val next = repo.add("next", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false)
        val done = repo.add("done", clock.nowMs + 120_000, "Asia/Dubai", isAlarm = false)
        repo.update(db.reminderDao().get(done)!!.copy(state = "DONE"))
        assertEquals(listOf(next), repo.observeUpcoming(clock.nowMs).first().map { it.id })
        assertTrue(past.isNotEmpty())
    }

    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)

    private suspend fun task(id: String, recurrence: String? = null) {
        db.taskDao().upsert(TaskEntity(id = id, listId = "list-1", title = id, recurrence = recurrence, sync = stamper.stamp()))
    }

    @Test fun snoozeSetsStateAndTime() = runBlocking {
        val id = repo.add("stretch", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = true)
        val before = db.reminderDao().get(id)!!
        val until = clock.nowMs + 600_000
        repo.snooze(id, until)
        val row = db.reminderDao().get(id)!!
        assertEquals("SNOOZED", row.state)
        assertEquals(until, row.snoozedUntil)
        assertEquals(before.fireAt, row.fireAt)
        assertTrue(row.sync.changeSeq > before.sync.changeSeq)
        assertEquals(listOf(id), repo.observeUpcoming(clock.nowMs + 300_000).first().map { it.id })
        assertEquals(listOf(id, id), changed)
    }

    @Test fun markDoneCompletesReminderAndItsTask() = runBlocking {
        task("task-1")
        val id = repo.add("buy milk", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false, taskId = "task-1")
        repo.snooze(id, clock.nowMs + 120_000)
        clock.nowMs += 5_000
        repo.markDone(id)
        val row = db.reminderDao().get(id)!!
        assertEquals("DONE", row.state)
        assertNull(row.snoozedUntil)
        val t = db.taskDao().get("task-1")!!
        assertEquals("DONE", t.status)
        assertEquals(clock.nowMs, t.completedAt)
        assertEquals(clock.nowMs, t.sync.updatedAt)
    }

    @Test fun dismissIsNotDone() = runBlocking {
        task("task-1")
        val id = repo.add("buy milk", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = true, taskId = "task-1")
        repo.dismiss(id)
        val row = db.reminderDao().get(id)!!
        assertEquals("DISMISSED", row.state)
        assertTrue(repo.observeUpcoming(0).first().isEmpty())
        val t = db.taskDao().get("task-1")!!
        assertEquals("dismissing never completes the task", "OPEN", t.status)
        assertNull(t.completedAt)
        assertEquals(listOf(id, id), changed)
    }

    @Test fun recurringReminderMovesToNextOccurrence() = runBlocking {
        task("task-1", recurrence = "FREQ=DAILY")
        val id = repo.add("water plants", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false, taskId = "task-1", recurrence = "FREQ=DAILY")
        val next = clock.nowMs + 86_460_000
        repo.snooze(id, clock.nowMs + 600_000)
        repo.dismiss(id, nextFireAt = next)
        var row = db.reminderDao().get(id)!!
        assertEquals("SCHEDULED", row.state)
        assertEquals(next, row.fireAt)
        assertNull(row.snoozedUntil)
        repo.markDone(id, nextFireAt = next + 86_400_000)
        row = db.reminderDao().get(id)!!
        assertEquals("SCHEDULED", row.state)
        assertEquals(next + 86_400_000, row.fireAt)
        assertNull("a recurring reminder leaves its task to the Tasks module", db.taskDao().get("task-1")!!.completedAt)
    }

    @Test fun markDoneLeavesRecurringOrFinishedTasksAlone() = runBlocking {
        task("task-r", recurrence = "FREQ=WEEKLY")
        val a = repo.add("a", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false, taskId = "task-r")
        repo.markDone(a)
        assertEquals("OPEN", db.taskDao().get("task-r")!!.status)
        task("task-d")
        db.taskDao().upsert(db.taskDao().get("task-d")!!.copy(status = "DONE", completedAt = 7L))
        val b = repo.add("b", clock.nowMs + 60_000, "Asia/Dubai", isAlarm = false, taskId = "task-d")
        repo.markDone(b)
        assertEquals(7L, db.taskDao().get("task-d")!!.completedAt)
    }

    @Test fun actionsOnUnknownIdsDoNothing() = runBlocking {
        repo.snooze("nope", 1L)
        repo.markDone("nope")
        repo.dismiss("nope")
        assertTrue(changed.isEmpty())
    }
}
