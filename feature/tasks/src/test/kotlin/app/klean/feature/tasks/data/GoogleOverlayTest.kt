package app.klean.feature.tasks.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.entity.TaskListEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.google.GoogleTaskEdits
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.inMemoryDb
import app.klean.feature.tasks.tempDevicePrefs
import app.klean.feature.tasks.ui.TaskViews
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Google Tasks overlay rules on the Tasks side (M21): edits are queued, nothing is ever created or deleted in Google. */
@RunWith(AndroidJUnit4::class)
class GoogleOverlayTest {
    private val nov10 = LocalDate(2026, 11, 10)
    private val clock = FakeClock(atMinute(nov10, 12 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val queued = mutableListOf<TaskEntity>()
    private var refreshes = 0
    private val google = object : GoogleTaskEdits {
        override suspend fun edited(task: TaskEntity) { queued += task }
        override suspend fun refreshSoon() { refreshes++ }
    }
    private val repo = DefaultTasksRepository(db, stamper, clock, DubaiZone, settings, emptySet(), setOf(google))
    private val gList = "g-list"
    private val gTask = "g-task"

    @After fun close() = db.close()

    private suspend fun seedGoogle() {
        repo.ensureDefaultLists()
        db.taskListDao().upsert(TaskListEntity(gList, "Groceries", "CUSTOM", 1000, source = "GOOGLE", remoteId = "L1", sync = stamper.stamp()))
        db.taskDao().upsert(
            TaskEntity(gTask, gList, "Milk", origin = "GOOGLE", remoteId = "t1", remoteListId = "L1", remoteEtag = "e1", sync = stamper.stamp()),
        )
    }

    @Test fun everyEditOfAGoogleTaskIsQueuedForGoogle() = runBlocking {
        seedGoogle()
        repo.complete(gTask)
        repo.reopen(gTask)
        repo.moveTo(gTask, nov10)
        repo.update(repo.get(gTask)!!.copy(title = "Oat milk", listId = DefaultTasksRepository.PERSONAL_ID))
        assertEquals(listOf(true, false, false, false), queued.map { it.completedAt != null })
        assertEquals("2026-11-10", queued[2].dueDate)
        // A Google task can't be moved to a Klean list (that would mean creating it in Google).
        val t = repo.get(gTask)!!
        assertEquals("Oat milk", t.title)
        assertEquals(gList, t.listId)
        assertEquals("t1", t.remoteId)
    }

    @Test fun kleanTasksAreNeverQueued() = runBlocking {
        repo.ensureDefaultLists()
        val id = repo.add(TaskDraft("Mine"))
        repo.complete(id)
        assertTrue(queued.isEmpty())
    }

    @Test fun aNewTaskAimedAtAGoogleListGoesToTheDefaultList() = runBlocking {
        seedGoogle()
        val id = repo.add(TaskDraft("Bread", listId = gList))
        val step = repo.add(TaskDraft("wholegrain", parentId = gTask))
        assertEquals(DefaultTasksRepository.PERSONAL_ID, repo.get(id)!!.listId)
        assertEquals("KLEAN", repo.get(id)!!.origin)
        assertEquals(DefaultTasksRepository.PERSONAL_ID, repo.get(step)!!.listId)
        assertTrue(queued.isEmpty())
    }

    @Test fun googleTasksAreNeverDeletedFromKlean() = runBlocking {
        seedGoogle()
        repo.delete(gTask)
        assertFalse(repo.get(gTask)!!.sync.deleted)
    }

    @Test fun visitingTasksAsksForARefreshAndHiddenListsDisappear() = runBlocking {
        seedGoogle()
        repo.refreshRemote()
        assertEquals(1, refreshes)
        val lists = repo.observeLists().first()
        val shown = TaskViews.build(repo.observeAll().first(), lists, nov10, gList)
        assertEquals(gList, shown.selectedListId)
        assertTrue(shown.listOpen.single().google)
        db.taskListDao().upsert(db.taskListDao().get(gList)!!.copy(selected = false))
        val hidden = TaskViews.build(repo.observeAll().first(), repo.observeLists().first(), nov10, gList)
        assertEquals(listOf("Personal", "Work"), hidden.lists.map { it.name })
        assertTrue(hidden.listOpen.none { it.google })
    }
}
