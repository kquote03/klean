package app.klean

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * M28: "Move to…" for every pair of modules, with the real handlers and database. Each capture is saved where
 * the router put it, moved to every other module, and the move must leave exactly the new record alive.
 */
@HiltAndroidTest
class CaptureMoveMatrixTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var handlers: Set<@JvmSuppressWildcards CaptureHandler>
    @Inject lateinit var gate: ModuleGate
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var clock: Clock

    private val modules = listOf(
        ModuleId.DIET, ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR,
        ModuleId.CONTACTS, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DELEGATION,
    )

    /** A phrase each module would naturally get. */
    private val phrase = mapOf(
        ModuleId.DIET to "two eggs and toast",
        ModuleId.TASKS to "buy batteries tomorrow",
        ModuleId.REMINDERS to "remind me to water the plants at 18:00",
        ModuleId.CALENDAR to "dentist friday 9:30",
        ModuleId.CONTACTS to "Matrix Test's birthday is March 3",
        ModuleId.WORKOUT to "30 pushups",
        ModuleId.SLEEP to "slept 23:30 to 7:00",
        ModuleId.DELEGATION to "ask Hermes to book a table",
    )

    @Before fun setUp() = runBlocking {
        hilt.inject()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = ApplicationProvider.getApplicationContext<Context>().packageName
        ui.grantRuntimePermission(pkg, Manifest.permission.READ_CONTACTS)
        ui.grantRuntimePermission(pkg, Manifest.permission.WRITE_CONTACTS)
        modules.forEach { gate.setEnabled(it, true) }
    }

    private suspend fun alive(module: ModuleId, id: String): Boolean = when (module) {
        ModuleId.DIET -> db.foodEntryDao().get(id)?.sync?.deleted == false
        ModuleId.TASKS, ModuleId.DELEGATION -> db.taskDao().get(id)?.sync?.deleted == false
        ModuleId.REMINDERS -> db.reminderDao().get(id)?.sync?.deleted == false
        ModuleId.CALENDAR -> db.eventDao().get(id)?.sync?.deleted == false
        ModuleId.CONTACTS -> db.contactDao().get(id)?.sync?.deleted == false
        ModuleId.WORKOUT -> db.workoutSessionDao().get(id)?.sync?.deleted == false
        ModuleId.SLEEP -> db.sleepDao().get(id)?.sync?.deleted == false
        else -> error("unknown $module")
    }

    @Test fun everyModuleHasAHandler() {
        assertEquals(modules.toSet(), handlers.map { it.moduleId }.toSet())
    }

    @Test fun phrasesRouteToTheirModuleWithoutAi() = runBlocking {
        for (m in modules) {
            val saved = capture.submit(CaptureInput(phrase.getValue(m), CaptureSource.DASHBOARD, clock.now().toEpochMilliseconds()))
                as CaptureResult.Saved
            assertEquals("\"${phrase[m]}\"", m, saved.moduleId)
            capture.undo(saved)
        }
    }

    @Test fun moveToWorksForEveryPair() = runBlocking {
        val failures = mutableListOf<String>()
        for (from in modules) for (to in modules) {
            if (from == to) continue
            val input = CaptureInput(phrase.getValue(from), CaptureSource.DASHBOARD, clock.now().toEpochMilliseconds(), targetModule = from)
            val saved = capture.submit(input) as CaptureResult.Saved
            assertEquals(from, saved.moduleId)
            assertTrue("$from record saved", alive(from, saved.recordId))
            val target = handlers.first { it.moduleId == to }.defaultAction(input)
            val moved = withTimeout(20_000) { capture.correct(saved, target) }
            val ok = moved.moduleId == to && alive(to, moved.recordId) && !alive(from, saved.recordId)
            if (!ok) failures += "$from → $to"
            // Moving back is offered.
            if (moved.alternatives.none { it.moduleId == from }) failures += "$from → $to: no way back"
            capture.undo(moved)
            assertFalse("$to record undone", alive(to, moved.recordId))
        }
        assertEquals(emptyList<String>(), failures)
    }
}
