package app.klean.feature.tasks.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.inMemoryDb
import app.klean.feature.tasks.tempDevicePrefs
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksRepositoryTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov10 = LocalDate(2026, 11, 10)
    private val clock = FakeClock(atMinute(nov9, 12 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private var changes = 0
    private val repo = DefaultTasksRepository(db, stamper, clock, DubaiZone, settings, setOf(TaskChangeListener { changes++ }))

    @After fun close() = db.close()

    @Test fun seedsPersonalAndWorkOnceWithStableIds() = runBlocking {
        repo.ensureDefaultLists()
        val lists = repo.observeLists().first()
        assertEquals(listOf("Personal", "Work"), lists.map { it.name })
        assertEquals(listOf(DefaultTasksRepository.PERSONAL_ID, DefaultTasksRepository.WORK_ID), lists.map { it.id })
        // A deleted default list stays deleted: the seed runs once.
        repo.deleteList(DefaultTasksRepository.WORK_ID)
        repo.ensureDefaultLists()
        assertEquals(listOf("Personal"), repo.observeLists().first().map { it.name })
    }

    @Test fun addStampsAndNotifies() = runBlocking {
        val id = repo.add(TaskDraft(title = "  pay rent  ", dueDate = nov10))
        val t = repo.get(id)!!
        assertEquals("pay rent", t.title)
        assertEquals("2026-11-10", t.dueDate)
        assertEquals(DefaultTasksRepository.PERSONAL_ID, t.listId)
        assertTrue(t.sync.changeSeq > 0)
        assertEquals(1, changes)
    }

    @Test fun completingARecurringTaskCreatesTheNextOneWithItsSubtasks() = runBlocking {
        val id = repo.add(TaskDraft(title = "water plants", dueDate = nov9, dueMinutes = 9 * 60, recurrence = "FREQ=WEEKLY;COUNT=2"))
        repo.add(TaskDraft(title = "balcony", parentId = id))
        val result = repo.complete(id)
        val done = repo.get(id)!!
        assertEquals("DONE", done.status)
        assertEquals(clock.nowMs, done.completedAt)
        val next = repo.get(result.nextId!!)!!
        assertEquals("2026-11-16", next.dueDate)
        assertEquals(9 * 60, next.dueMinutes)
        assertEquals("FREQ=WEEKLY;COUNT=1", next.recurrence)
        assertEquals("OPEN", next.status)
        assertEquals(listOf("balcony"), repo.subtasks(next.id).map { it.title })
        // The last of the series makes no new one.
        assertNull(repo.complete(next.id).nextId)
    }

    @Test fun recurringTaskWithoutDateRepeatsFromToday() = runBlocking {
        val id = repo.add(TaskDraft(title = "stretch", recurrence = "FREQ=DAILY;INTERVAL=2"))
        val next = repo.get(repo.complete(id).nextId!!)!!
        assertEquals("2026-11-11", next.dueDate)
    }

    @Test fun undoCompleteReopensAndRemovesTheNextOne() = runBlocking {
        val id = repo.add(TaskDraft(title = "bins", dueDate = nov9, recurrence = "FREQ=DAILY"))
        val result = repo.complete(id)
        repo.undoComplete(result)
        val t = repo.get(id)!!
        assertEquals("OPEN", t.status)
        assertNull(t.completedAt)
        assertTrue(repo.get(result.nextId!!)!!.sync.deleted)
    }

    @Test fun deleteTombstonesSubtasksToo() = runBlocking {
        val id = repo.add(TaskDraft(title = "trip"))
        val sub = repo.add(TaskDraft(title = "passport", parentId = id))
        repo.delete(id)
        assertTrue(repo.get(id)!!.sync.deleted)
        assertTrue(repo.get(sub)!!.sync.deleted)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test fun reviewActions() = runBlocking {
        val a = repo.add(TaskDraft(title = "a", dueDate = LocalDate(2026, 11, 7)))
        val b = repo.add(TaskDraft(title = "b", dueDate = LocalDate(2026, 11, 8)))
        val c = repo.add(TaskDraft(title = "c", dueDate = LocalDate(2026, 11, 8)))
        repo.add(TaskDraft(title = "today", dueDate = nov9))
        assertEquals(listOf("a", "b", "c"), repo.pastDue().map { it.title })

        repo.keepForToday(a)
        assertEquals("2026-11-09", repo.get(a)!!.dueDate)
        repo.moveTo(b, LocalDate(2026, 11, 20))
        assertEquals("2026-11-20", repo.get(b)!!.dueDate)
        repo.someday(c)
        assertEquals("SOMEDAY", repo.get(c)!!.status)
        assertNull(repo.get(c)!!.dueDate)
        assertTrue(repo.pastDue().isEmpty())
    }

    @Test fun archivesTasksDoneMoreThanThirtyDaysAgo() = runBlocking {
        val old = repo.add(TaskDraft(title = "old"))
        repo.complete(old)
        clock.nowMs += 20L * 86_400_000
        val recent = repo.add(TaskDraft(title = "recent"))
        repo.complete(recent)
        clock.nowMs += 11L * 86_400_000
        assertEquals(1, repo.archiveOldCompleted())
        assertNotNull(repo.get(old)!!.archivedAt)
        assertNull(repo.get(recent)!!.archivedAt)
        assertEquals(listOf("recent"), repo.observeAll().first().map { it.title })
        assertEquals(0, repo.archiveOldCompleted())
    }

    @Test fun reopenClearsDone() = runBlocking {
        val id = repo.add(TaskDraft(title = "x"))
        repo.complete(id)
        repo.reopen(id)
        assertEquals("OPEN", repo.get(id)!!.status)
        assertFalse(repo.get(id)!!.completedAt != null)
    }

    @Test fun listsAddRenameAndDeleteWithTheirTasks() = runBlocking {
        repo.ensureDefaultLists()
        val l = repo.addList(" Groceries ")
        val t = repo.add(TaskDraft(title = "milk", listId = l))
        repo.renameList(l, "Shopping")
        assertEquals("Shopping", repo.observeLists().first().last().name)
        repo.deleteList(l)
        assertTrue(repo.get(t)!!.sync.deleted)
        assertEquals(listOf("Personal", "Work"), repo.observeLists().first().map { it.name })
    }
}
