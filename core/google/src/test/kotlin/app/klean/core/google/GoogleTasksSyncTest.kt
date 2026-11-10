package app.klean.core.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.sync.SyncStamper
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleTasksSyncTest {
    private val clock = FakeClock(1_762_768_800_000) // 2025-11-10T10:00Z
    private val scope = tempScope()
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock, scope), clock)
    private val state = tempGoogleState(scope)
    private val auth = FakeGoogleAuth(connected = true)
    private val server = FakeGoogleServer()
    private val sync = GoogleTasksSync(db, stamper, auth, server.client(auth), state, clock)
    private val edits = { t: app.klean.core.data.db.entity.TaskEntity -> runBlocking { t.toOutboxOp(clock.now().toEpochMilliseconds())?.let { state.enqueue(it) } } }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.coroutineContext.job.cancelAndJoin()
    }

    private fun seed() {
        server.list("L1", "Groceries")
        server.list("L2", "Errands")
        server.task("L1", GTask("a", "Milk", due = "2026-11-12T00:00:00.000Z"))
        server.task("L1", GTask("b", "Eggs", status = "completed", completed = "2025-11-09T08:00:00.000Z"))
        server.task("L1", GTask("c", "Brown ones", parent = "b"))
        server.task("L2", GTask("d", "Post office", notes = "Before 5"))
        server.task("L2", GTask("e", "Gone", deleted = true))
    }

    @Test fun pullCachesListsAfterKleansAndTasksWithOriginGoogle() = runBlocking {
        db.taskListDao().upsert(TaskListEntity("klean", "Personal", "PERSONAL", 0, sync = stamper.stamp()))
        seed()
        val r = sync.run()
        assertTrue(r is GoogleSyncResult.Done)
        val lists = db.taskListDao().observeAll().first()
        assertEquals(listOf("Personal", "Groceries", "Errands"), lists.map { it.name })
        assertEquals(listOf("KLEAN", "GOOGLE", "GOOGLE"), lists.map { it.source })
        val milk = db.taskDao().get(GoogleTasksSync.taskId("a"))!!
        assertEquals("GOOGLE", milk.origin)
        assertEquals("a", milk.remoteId)
        assertEquals("L1", milk.remoteListId)
        assertEquals("2026-11-12", milk.dueDate)
        assertEquals(GoogleTasksSync.listId("L1"), milk.listId)
        val eggs = db.taskDao().get(GoogleTasksSync.taskId("b"))!!
        assertEquals("DONE", eggs.status)
        assertEquals(1_762_675_200_000, eggs.completedAt)
        assertEquals(GoogleTasksSync.taskId("b"), db.taskDao().get(GoogleTasksSync.taskId("c"))!!.parentId)
        assertEquals("Before 5", db.taskDao().get(GoogleTasksSync.taskId("d"))!!.notes)
        assertNull(db.taskDao().get(GoogleTasksSync.taskId("e")))
        assertTrue(state.lastSyncAt.first() != null)
    }

    @Test fun aSecondPullWithNoChangesWritesNothing() = runBlocking {
        seed()
        sync.run()
        val seq = db.changeCounterDao().next()
        val r = sync.run() as GoogleSyncResult.Done
        assertEquals(0, r.tasks)
        assertEquals(seq + 1, db.changeCounterDao().next())
    }

    @Test fun tasksRemovedInGoogleAndUnselectedListsLeaveKlean() = runBlocking {
        seed()
        sync.run()
        server.items.removeAll { it.task.id == "a" }
        sync.setSelected(GoogleTasksSync.listId("L2"), false)
        sync.run()
        assertTrue(db.taskDao().get(GoogleTasksSync.taskId("a"))!!.sync.deleted)
        assertTrue(db.taskDao().get(GoogleTasksSync.taskId("d"))!!.sync.deleted)
        assertEquals(false, db.taskListDao().get(GoogleTasksSync.listId("L2"))!!.selected)
        // Back on: the tasks return.
        sync.setSelected(GoogleTasksSync.listId("L2"), true)
        sync.run()
        assertEquals(false, db.taskDao().get(GoogleTasksSync.taskId("d"))!!.sync.deleted)
    }

    @Test fun offlineEditsWaitInTheOutboxAndReplayAsPatchesThenLastWriteWins() = runBlocking {
        seed()
        sync.run()
        val milk = db.taskDao().get(GoogleTasksSync.taskId("a"))!!
        server.offline = true
        edits(milk.copy(title = "Milk (oat)", completedAt = clock.now().toEpochMilliseconds(), status = "DONE"))
        assertEquals(GoogleSyncResult.Failed(retry = true), sync.run())
        assertEquals(1, state.outbox().size)
        // A second edit of the same task replaces the first.
        clock.nowMs += 1_000
        edits(milk.copy(title = "Oat milk", dueDate = null, completedAt = clock.now().toEpochMilliseconds(), status = "DONE"))
        assertEquals(1, state.outbox().size)

        server.offline = false
        val r = sync.run() as GoogleSyncResult.Done
        assertEquals(1, r.pushed)
        assertTrue(state.outbox().isEmpty())
        assertEquals(1, server.patches.size)
        val body = server.patches.single().second
        assertEquals("Oat milk", body["title"]!!.jsonPrimitive.content)
        assertEquals("completed", body["status"]!!.jsonPrimitive.content)
        val g = server.items.single { it.task.id == "a" }.task
        assertEquals("Oat milk", g.title)
        assertNull(g.due)
    }

    @Test fun anEditOfATaskGoogleNoLongerHasIsDropped() = runBlocking {
        seed()
        sync.run()
        val milk = db.taskDao().get(GoogleTasksSync.taskId("a"))!!
        server.items.removeAll { it.task.id == "a" }
        edits(milk.copy(title = "x"))
        sync.run()
        assertTrue(state.outbox().isEmpty())
        assertTrue(db.taskDao().get(milk.id)!!.sync.deleted)
    }

    @Test fun kleanTasksNeverQueueAnything() = runBlocking {
        val t = app.klean.core.data.db.entity.TaskEntity("k", "list", "Mine", sync = SyncColumns(0, 0, "d", false, 0))
        assertNull(t.toOutboxOp(0))
    }

    @Test fun disconnectRemovesGoogleRowsAndTheOutbox() = runBlocking {
        seed()
        sync.run()
        edits(db.taskDao().get(GoogleTasksSync.taskId("a"))!!.copy(title = "x"))
        sync.forget()
        assertTrue(state.outbox().isEmpty())
        assertTrue(db.taskListDao().observeAll().first().none { it.source == "GOOGLE" })
        assertTrue(db.taskDao().observeAll().first().none { it.origin == "GOOGLE" })
    }

    @Test fun notConnectedDoesNothing() = runBlocking {
        auth.disconnect()
        assertEquals(GoogleSyncResult.NotConnected, sync.run())
        assertTrue(server.requests.isEmpty())
    }
}
