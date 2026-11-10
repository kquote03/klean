package app.klean.feature.tasks.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.voice.VoiceRecognizer
import app.klean.core.design.theme.KleanTheme
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.FakeGate
import app.klean.feature.tasks.FakeTasksRepository
import app.klean.feature.tasks.FakeVoice
import app.klean.feature.tasks.data.TaskDraft
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class TasksScreenTest {
    @get:Rule val rule = createComposeRule()
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 10 * 60, Dubai))
    private val repo = FakeTasksRepository(nov9)
    private val store = ViewModelStore()
    private val days = object : DayTicker(clock, DubaiZone) { override fun days() = flowOf(nov9) }

    private fun vm(gate: FakeGate = FakeGate()) = TasksViewModel(repo, days, clock, DubaiZone, gate, FakeVoice() as VoiceRecognizer)
        .also { store.put("vm", it) }

    @After fun clear() = store.clear()

    private fun show(vm: TasksViewModel, tab: String? = null) = rule.setContent {
        KleanTheme { TasksScreen(initialTab = tab, onOpenTask = {}, onNewTask = {}, onEditReminder = {}, vm = vm) }
    }

    /** The review chips moved to a long press: the row stays calm, every choice is still one gesture away. */
    @Test fun stillWantTheseComesLastAndKeepsItsChoicesOnALongPress() {
        val old = runBlocking { repo.add(TaskDraft("renew card", dueDate = LocalDate(2026, 11, 6))) }
        runBlocking { repo.add(TaskDraft("call bank", dueDate = nov9)) }
        show(vm())
        rule.onNodeWithText("Still want these?").assertIsDisplayed()
        rule.onNodeWithText("call bank").assertIsDisplayed()
        rule.onNodeWithText("Keep for today").assertDoesNotExist()
        rule.onNodeWithText("renew card").performTouchInput { longClick() }
        rule.onNodeWithText("Keep for today").performClick()
        rule.waitForIdle()
        assertEquals("2026-11-09", runBlocking { repo.get(old)!!.dueDate })
        rule.onNodeWithText("Still want these?").assertDoesNotExist()
    }

    @Test fun checkCircleCompletesWithALabel() {
        val id = runBlocking { repo.add(TaskDraft("bins", dueDate = nov9)) }
        show(vm())
        rule.onNodeWithContentDescription("Mark bins done").performClick()
        rule.waitForIdle()
        assertEquals("DONE", runBlocking { repo.get(id)!!.status })
    }

    @Test fun remindersLeaveTheMenuWhenRemindersIsOff() {
        show(vm(FakeGate(setOf(ModuleId.REMINDERS))))
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Lists").assertIsDisplayed()
        rule.onNodeWithText("Reminders").assertDoesNotExist()
    }

    @Test fun deepLinkTabOpensListsAndAllTasksComesBack() {
        runBlocking { repo.add(TaskDraft("slides", listId = "w")) }
        show(vm(), tab = "lists")
        rule.onNodeWithText("Personal").assertIsDisplayed()
        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("slides").assertIsDisplayed()
        rule.onNodeWithText("All tasks").performClick()
        rule.onNodeWithText("Personal").assertDoesNotExist()
    }

    @Test fun mainViewIsWithinTheClutterBudget() {
        runBlocking {
            repo.add(TaskDraft("renew card", dueDate = LocalDate(2026, 11, 6)))
            repeat(6) { repo.add(TaskDraft("task $it", dueDate = nov9)) }
            repo.add(TaskDraft("dentist", dueDate = LocalDate(2026, 11, 12)))
        }
        show(vm())
        rule.waitForIdle()
        rule.assertClutterBudget()
    }
}
