package app.klean.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.reminder.ReminderRepository
import app.klean.feature.reminders.ui.RepeatChoice
import app.klean.feature.reminders.ui.RepeatKind
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.FakeTasksRepository
import app.klean.feature.tasks.data.TaskDraft
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.atMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TaskEditViewModelTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 10 * 60, Dubai))
    private val repo = FakeTasksRepository(nov9)
    private val store = ViewModelStore()

    private class FakeReminders : ReminderRepository {
        val rows = MutableStateFlow<List<ReminderEntity>>(emptyList())
        override suspend fun add(title: String, fireAt: Long, zone: String, isAlarm: Boolean, taskId: String?, recurrence: String?): String {
            val id = "r${rows.value.size}"
            rows.value += ReminderEntity(id, taskId, title, fireAt, zone, isAlarm, recurrence, sync = SyncColumns(0, 0, "d", false, 1))
            return id
        }
        override suspend fun update(r: ReminderEntity) { rows.value = rows.value.map { if (it.id == r.id) r else it } }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun snooze(id: String, until: Long) = Unit
        override suspend fun markDone(id: String, nextFireAt: Long?) = Unit
        override suspend fun dismiss(id: String, nextFireAt: Long?) = Unit
        override fun observeForTask(taskId: String): Flow<List<ReminderEntity>> = rows.map { l -> l.filter { it.taskId == taskId } }
        override fun observeUpcoming(fromMs: Long): Flow<List<ReminderEntity>> = rows
    }
    private val reminders = FakeReminders()

    private fun vm(taskId: String? = null, parentId: String? = null): TaskEditViewModel {
        val v = TaskEditViewModel(SavedStateHandle(mapOf("taskId" to taskId, "parentId" to parentId)), repo, reminders, clock, DubaiZone)
        store.put("vm${System.nanoTime()}", v)
        return v
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun newTaskSavesWithRepeatAnchoredOnItsDate() = runTest {
        val vm = vm()
        vm.setTitle("water plants")
        vm.setDue(LocalDate(2026, 11, 11))
        vm.setRepeat(RepeatChoice(RepeatKind.EVERY_N_WEEKS, 2))
        vm.setPriority(2)
        vm.save()
        val t = repo.tasks.value.single()
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE", t.recurrence)
        assertEquals(2, t.priority)
        assertTrue(vm.state.value.closed)
    }

    @Test fun invalidCustomRuleCannotBeSaved() = runTest {
        val vm = vm()
        vm.setTitle("x")
        vm.setRepeat(RepeatChoice(RepeatKind.CUSTOM, custom = "every tuesday"))
        assertFalse(vm.state.value.canSave)
        vm.setRepeat(RepeatChoice(RepeatKind.CUSTOM, custom = "FREQ=WEEKLY;BYDAY=TU"))
        assertTrue(vm.state.value.canSave)
    }

    @Test fun stepsAndPresetRemindersOnAnExistingTask() = runTest {
        val id = repo.add(TaskDraft("trip", dueDate = nov9, dueMinutes = 15 * 60))
        val vm = vm(id)
        vm.subtasks.test {
            awaitItem()
            vm.addSubtask("passport")
            assertEquals(listOf("passport"), expectMostRecentItem().map { it.title })
        }
        vm.taskReminders.test {
            awaitItem()
            vm.addReminder(ReminderPreset.HOUR_BEFORE)
            val r = expectMostRecentItem().single()
            assertEquals(atMinute(nov9, 15 * 60, Dubai) - HOUR_MS, r.fireAt)
            assertEquals(id, r.taskId)
            assertFalse(r.isAlarm)
            vm.setReminderAlarm(r, true)
            assertTrue(expectMostRecentItem().single().isAlarm)
        }
    }

    @Test fun allDayTaskRemindsAtNine() = runTest {
        val id = repo.add(TaskDraft("bins", dueDate = nov9))
        val vm = vm(id)
        assertEquals(atMinute(nov9, 9 * 60, Dubai), vm.presetTime(ReminderPreset.AT_DUE))
    }

    @Test fun datingASomedayTaskBringsItBack() = runTest {
        val id = repo.add(TaskDraft("paint"))
        repo.someday(id)
        val vm = vm(id)
        assertTrue(vm.state.value.someday)
        vm.setDue(LocalDate(2026, 11, 20))
        vm.save()
        assertEquals("OPEN", repo.get(id)!!.status)
        assertEquals("2026-11-20", repo.get(id)!!.dueDate)
    }

    @Test fun deleteRemovesTheTaskAndItsReminders() = runTest {
        val id = repo.add(TaskDraft("trip", dueDate = nov9))
        val vm = vm(id)
        vm.taskReminders.test {
            awaitItem()
            vm.addReminder(ReminderPreset.AT_DUE)
            awaitItem()
            vm.delete()
            assertTrue(expectMostRecentItem().isEmpty())
        }
        assertTrue(repo.get(id)!!.sync.deleted)
    }
}
