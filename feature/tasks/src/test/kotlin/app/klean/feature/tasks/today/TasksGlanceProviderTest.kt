package app.klean.feature.tasks.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ui.module.QuickAction
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.FakeTasksRepository
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TaskStatus
import app.klean.feature.tasks.ui.DayTicker
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nov9 = LocalDate(2026, 11, 9)
    private val nov8 = LocalDate(2026, 11, 8)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(nov9, 12))
    private val repo = FakeTasksRepository(nov9)
    private val days = object : DayTicker(clock, DubaiZone) { override fun days() = flowOf(nov9) }
    private val provider = TasksGlanceProvider(ctx, { repo }, days, clock, DubaiZone)

    private suspend fun completeAt(id: String, ms: Long) =
        repo.update(repo.get(id)!!.copy(status = TaskStatus.DONE, completedAt = ms))

    @Test fun earlierDaysAreOneCalmRow() = runBlocking {
        repo.add(TaskDraft("old one", dueDate = nov8))
        repo.add(TaskDraft("older", dueDate = LocalDate(2026, 11, 1)))
        repo.add(TaskDraft("oldest", dueDate = LocalDate(2026, 10, 1)))
        val g = provider.glance().first()
        val row = g.attention.single()
        assertEquals("3 from earlier days", row.text)
        assertEquals("klean://tasks", row.route)
        assertTrue(g.upcoming.isEmpty())
    }

    @Test fun todaysTasksAreComingUpAndTickable() = runBlocking {
        repo.add(TaskDraft("call bank", dueDate = nov9, dueMinutes = 15 * 60))
        repo.add(TaskDraft("bins", dueDate = nov9))
        repo.add(TaskDraft("tomorrow", dueDate = LocalDate(2026, 11, 10)))
        repo.add(TaskDraft("standup notes", dueDate = nov9, dueMinutes = 9 * 60))
        val g = provider.glance().first()
        assertEquals(setOf("call bank", "bins"), g.upcoming.map { it.text }.toSet())
        assertEquals(at(nov9, 15), g.upcoming.first { it.text == "call bank" }.at)
        assertTrue(g.upcoming.all { it.checkable && it.module == ModuleId.TASKS })
        // A timed task whose time has passed today needs attention instead of vanishing.
        assertEquals(listOf("standup notes"), g.attention.map { it.text })
        provider.check(g.upcoming.first { it.text == "bins" })
        assertTrue(repo.tasks.value.first { it.title == "bins" }.completedAt != null)
    }

    @Test fun doneTodayOnlyWithTimes() = runBlocking {
        val a = repo.add(TaskDraft("paid rent", dueDate = nov9))
        val b = repo.add(TaskDraft("yesterday's", dueDate = nov8))
        completeAt(a, at(nov9, 9))
        // Completed late last night (23:50) is yesterday's, even when looked at just after midnight.
        completeAt(b, at(nov8, 23, 50))
        clock.nowMs = at(nov9, 0, 30)
        val done = provider.glance().first().done
        assertEquals(listOf("paid rent"), done.map { it.text })
        assertEquals(at(nov9, 9), done.single().at)
    }

    @Test fun taskIsOneTapCaptureAndCalm() = runBlocking {
        repo.add(TaskDraft("old one", dueDate = nov8))
        val g = provider.glance().first()
        assertEquals(QuickAction.Capture(ModuleId.TASKS, "Task", g.quickAction!!.icon), g.quickAction)
        val copy = (g.attention + g.upcoming + g.done).map { it.text } + g.quickAction!!.label
        assertFalse(copy.any { "!" in it || "overdue" in it.lowercase() || "missed" in it.lowercase() })
    }
}
