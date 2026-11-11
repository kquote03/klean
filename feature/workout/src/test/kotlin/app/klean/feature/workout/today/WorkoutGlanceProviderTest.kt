package app.klean.feature.workout.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.QuickAction
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.DubaiZone
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.WorkoutStartSheetRoute
import app.klean.feature.workout.data.DefaultWorkoutRepository
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind
import app.klean.feature.workout.tempDevicePrefs
import app.klean.feature.workout.ui.WorkoutFormatter
import app.klean.shared.module.ModuleId
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

@RunWith(AndroidJUnit4::class)
class WorkoutGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate(2026, 11, 10)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = atMinute(d, h * 60 + m, Dubai)
    private val clock = FakeClock(at(7))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val repo = DefaultWorkoutRepository(db, stamper, clock, emptySet())
    private val provider = WorkoutGlanceProvider(ctx, { repo }, WorkoutFormatter(ctx, DubaiZone), clock, DubaiZone)

    @After fun close() = db.close()

    @Test fun aRunningSessionNeedsAttention() = runBlocking {
        repo.start()
        clock.nowMs = at(7, 24)
        val g = provider.glance().first()
        assertEquals(listOf("Workout in progress"), g.attention.map { it.text })
        assertEquals("24 min", g.attention.single().detail)
        assertTrue(g.done.isEmpty())
    }

    @Test fun todaysFinishedSessionsWithWhatWasDone() = runBlocking {
        clock.nowMs = at(22, 0, LocalDate(2026, 11, 9))
        repo.finish(repo.start().also { repo.addSet(it, WorkoutItem("yesterday lunges", WorkoutKind.STRENGTH, reps = 10)) })
        clock.nowMs = at(7)
        val id = repo.start()
        repo.logItems(id, listOf(WorkoutItem("pushups", WorkoutKind.STRENGTH, sets = 3, reps = 12)))
        clock.nowMs = at(7, 25)
        repo.finish(id)
        clock.nowMs = at(12)
        val g = provider.glance().first()
        val row = g.done.single()
        assertEquals("pushups", row.text)
        assertEquals(at(7, 25), row.at)
        assertTrue(row.detail!!.contains("25 min"))
        assertEquals(ModuleId.WORKOUT, row.module)
        assertFalse((g.done.map { it.text } + listOfNotNull(row.detail)).any { "!" in it })
    }

    @Test fun workoutOpensTheStartSheet() = runBlocking {
        val a = provider.glance().first().quickAction
        assertEquals(QuickAction.Open(ModuleId.WORKOUT, "Workout", a!!.icon, WorkoutStartSheetRoute), a)
    }
}
