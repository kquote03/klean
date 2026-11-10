package app.klean.feature.tasks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.StatusSurface
import app.klean.feature.tasks.data.DefaultTasksRepository
import app.klean.feature.tasks.data.TaskChangeListener
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.ui.DayTicker
import app.klean.feature.tasks.ui.TaskFormatter
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasksModuleTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 10 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private var refreshes = 0
    private val days = object : DayTicker(clock, DubaiZone) { override fun days() = flowOf(nov9) }
    private lateinit var surfaces: TaskSurfaces
    private val repo = DefaultTasksRepository(db, stamper, clock, DubaiZone, settings, setOf(TaskChangeListener { surfaces.onTasksChanged() }))

    init {
        surfaces = TaskSurfaces(
            ctx, { repo }, days, TaskFormatter(ctx, DubaiZone), DubaiZone,
            { StatusRefresh { setOf(object : StatusSurface { override suspend fun refresh() { refreshes++ } }) } },
        )
    }

    @After fun close() = db.close()

    @Test fun captureMakesADatedTask() = runBlocking {
        val h = TaskCaptureHandler(repo, DubaiZone)
        val input = CaptureInput("pay rent friday", CaptureSource.QS_TILE, clock.nowMs)
        val id = h.execute(input, CandidateAction(ModuleId.TASKS, RuleCaptureClassifier.ADD_TASK, 1f))
        val t = repo.get(id)!!
        assertEquals("pay rent", t.title)
        assertEquals("2026-11-13", t.dueDate)
        assertEquals("CAPTURE", t.source)
        h.undo(id)
        assertTrue(repo.get(id)!!.sync.deleted)
        val plain = repo.get(h.execute(input.copy(text = "buy milk"), h.defaultAction(input)))!!
        assertNull(plain.dueDate)
    }

    @Test fun statusLineTileAndTodayItemsFollowWrites() = runBlocking {
        surfaces.fragments().test {
            assertEquals(emptyList<Any>(), awaitItem())
            repo.add(TaskDraft("call bank", dueDate = nov9, dueMinutes = 14 * 60))
            var l = awaitItem()
            while (l.isEmpty()) l = awaitItem()
            assertEquals("1 task", l.single().text)
            repo.add(TaskDraft("bins", dueDate = nov9))
            while (l.single().text == "1 task") l = awaitItem()
            assertEquals("2 tasks", l.single().text)
            cancelAndIgnoreRemainingEvents()
        }
        surfaces.summaries().test { assertEquals("2 for today", awaitItem().single().value) }
        surfaces.items().test {
            val items = awaitItem()
            assertEquals(listOf("bins", "call bank"), items.sortedBy { it.sortKey }.map { it.text })
            assertEquals("klean://tasks", items.first().route)
        }
        assertTrue(refreshes >= 2)
    }

    @Test fun weekActivityCountsTasksPerDueDay() = runBlocking {
        repo.add(TaskDraft("a", dueDate = nov9))
        repo.add(TaskDraft("b", dueDate = nov9))
        repo.add(TaskDraft("c", dueDate = LocalDate(2026, 11, 11)))
        repo.add(TaskDraft("undated"))
        surfaces.activity(nov9, LocalDate(2026, 11, 15)).test {
            assertEquals(mapOf(nov9 to 2, LocalDate(2026, 11, 11) to 1), awaitItem())
        }
    }
}
