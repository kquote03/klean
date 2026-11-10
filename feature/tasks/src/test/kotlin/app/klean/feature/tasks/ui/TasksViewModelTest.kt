package app.klean.feature.tasks.ui

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.FakeGate
import app.klean.feature.tasks.FakeTasksRepository
import app.klean.feature.tasks.FakeVoice
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    private val today = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(today, 10 * 60, Dubai))
    private val repo = FakeTasksRepository(today)
    private val gate = FakeGate()
    private val voice = FakeVoice()
    private val store = ViewModelStore()

    private val days = object : DayTicker(clock, DubaiZone) {
        override fun days() = flowOf(today)
    }

    private fun vm(): TasksViewModel {
        val v = TasksViewModel(repo, days, clock, DubaiZone, gate, voice as VoiceRecognizer)
        store.put("vm${System.nanoTime()}", v)
        return v
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun typedTaskOnTodayIsDueToday() = runTest {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            vm.draft.value = "call the bank"
            vm.submit()
            val s = expectMostRecentItem()
            assertEquals(listOf("call the bank"), s.views.today.map { it.title })
            assertEquals("", vm.draft.value)
        }
    }

    @Test fun dateWordsWinOverTheTab() = runTest {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            vm.draft.value = "pay rent friday"
            vm.submit()
            val s = expectMostRecentItem()
            assertEquals(LocalDate(2026, 11, 13), s.views.upcoming.single().date)
            assertEquals("pay rent", s.views.upcoming.single().rows.single().title)
        }
    }

    @Test fun onListsTabTasksGoToTheSelectedList() = runTest {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            vm.selectTab(TasksTab.LISTS)
            vm.selectList("w")
            vm.draft.value = "slides"
            vm.submit()
            val s = expectMostRecentItem()
            assertEquals("w", s.views.selectedListId)
            assertEquals(listOf("slides"), s.views.listOpen.map { it.title })
            assertEquals(null, repo.drafts.single().dueDate)
        }
    }

    @Test fun completingEmitsUndoableEvent() = runTest {
        val vm = vm()
        val id = repo.add(app.klean.feature.tasks.data.TaskDraft("bins", dueDate = today))
        vm.events.test {
            vm.setDone(id, true, "bins")
            val e = awaitItem() as TasksEvent.Completed
            assertEquals(id, e.completion.id)
            vm.undoComplete(e.completion)
        }
        assertEquals(null, repo.get(id)!!.completedAt)
    }

    @Test fun reviewActionsMoveTasksOut() = runTest {
        val a = repo.add(app.klean.feature.tasks.data.TaskDraft("a", dueDate = LocalDate(2026, 11, 7)))
        val b = repo.add(app.klean.feature.tasks.data.TaskDraft("b", dueDate = LocalDate(2026, 11, 8)))
        val vm = vm()
        vm.uiState.test {
            assertEquals(2, expectMostRecentItem().views.review.size)
            vm.keepForToday(a)
            vm.someday(b)
            val s = expectMostRecentItem()
            assertTrue(s.views.review.isEmpty())
            assertEquals(listOf("a"), s.views.today.map { it.title })
            assertEquals(listOf("b"), s.views.listSomeday.map { it.title })
        }
    }

    @Test fun remindersTabFollowsTheGate() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertTrue(expectMostRecentItem().remindersOn)
            gate.setEnabled(ModuleId.REMINDERS, false)
            assertFalse(expectMostRecentItem().remindersOn)
        }
    }

    @Test fun voiceSavesAndUndoRestoresText() = runTest {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            vm.voice.onMic()
            voice.listener!!.onFinal("water plants tomorrow")
            assertEquals(listOf("water plants"), expectMostRecentItem().views.upcoming.single().rows.map { it.title })
            vm.voice.undoLast()
            assertTrue(expectMostRecentItem().views.upcoming.isEmpty())
            assertEquals("water plants tomorrow", vm.draft.value)
        }
    }

    @Test fun newListBecomesSelected() = runTest {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            vm.addList("Groceries")
            val s = expectMostRecentItem()
            assertEquals("Groceries", s.views.lists.first { it.id == s.views.selectedListId }.name)
        }
    }
}
