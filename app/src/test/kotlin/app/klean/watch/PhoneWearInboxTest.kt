package app.klean.watch

import app.klean.core.capture.UniversalCaptureService
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.StatusSurface
import app.klean.nav.FakeModuleGate
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WearPaths
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** What the phone does with each message from the watch (WearSyncService → PhoneWearInbox). */
// Robolectric: the code under test logs through android.util.Log.
@RunWith(AndroidJUnit4::class)
class PhoneWearInboxTest {
    private val diet = RecordingHandler(ModuleId.DIET)
    private val tasks = RecordingHandler(ModuleId.TASKS)
    private val gate = FakeModuleGate()
    private val workouts = FakeWatchWorkouts()
    private var refreshes = 0
    private val refresh = StatusRefresh { setOf(object : StatusSurface { override suspend fun refresh() { refreshes++ } }) }
    private val inbox = PhoneWearInbox(
        UniversalCaptureService(setOf(diet, tasks), gate, TargetRouter()),
        workouts, gate, refresh, SteppingClock(42),
    )

    private suspend fun send(m: WatchMessage): Boolean = WearCodec.encode(m).let { (path, bytes) -> inbox.onMessage(path, bytes) }

    @Test fun aVoiceCaptureGoesThroughUniversalCaptureWithSourceWatch() = runTest {
        assertTrue(send(WatchMessage.Capture("call the bank tomorrow", module = "tasks", viaVoice = true)))
        val input = tasks.executed.single()
        assertEquals("call the bank tomorrow", input.text)
        assertEquals(CaptureSource.WATCH, input.source)
        assertTrue(input.viaVoice)
        assertEquals(ModuleId.TASKS, input.targetModule)
        assertEquals(42L, input.capturedAt)
        assertEquals(1, refreshes)
    }

    @Test fun aCaptureWithNoModuleIsRoutedByThePhone() = runTest {
        assertTrue(send(WatchMessage.Capture("2 eggs")))
        assertEquals(null, diet.executed.single().targetModule)
        assertFalse(diet.executed.single().viaVoice)
    }

    @Test fun aFoodChipLogsToDiet() = runTest {
        assertTrue(send(WatchMessage.LogFood("oat latte")))
        val input = diet.executed.single()
        assertEquals("oat latte", input.text)
        assertEquals(ModuleId.DIET, input.targetModule)
        assertEquals(CaptureSource.WATCH, input.source)
        assertTrue(tasks.executed.isEmpty())
    }

    @Test fun startAndLogSetDriveTheWorkoutSession() = runTest {
        assertTrue(send(WatchMessage.StartWorkout("tpl-1")))
        assertEquals(listOf("tpl-1"), workouts.started)
        assertTrue(send(WatchMessage.LogSet("s1", "Squat", 10, 40.0)))
        assertEquals(listOf(listOf<Any?>("s1", "Squat", 10, 40.0)), workouts.sets)
        // An unknown template or a closed session changes nothing.
        assertFalse(send(WatchMessage.StartWorkout("gone")))
        assertFalse(send(WatchMessage.LogSet("old", "Squat", 5)))
        assertEquals(1, workouts.sets.size)
    }

    @Test fun workoutMessagesAreIgnoredWithWorkoutOff() = runTest {
        gate.setEnabled(ModuleId.WORKOUT, false)
        assertFalse(send(WatchMessage.StartWorkout("tpl-1")))
        assertFalse(send(WatchMessage.LogSet("s1", "Squat", 10)))
        assertTrue(workouts.started.isEmpty() && workouts.sets.isEmpty())
    }

    @Test fun garbageWrongPathsAndThePhonesOwnAlarmPingAreIgnored() = runTest {
        assertFalse(inbox.onMessage(WearPaths.CAPTURE, "not json".encodeToByteArray()))
        assertFalse(inbox.onMessage(WearPaths.CAPTURE, WearCodec.encode(WatchMessage.LogFood("tea")).second))
        assertFalse(inbox.onMessage("/other/app", WearCodec.encode(WatchMessage.Capture("tea")).second))
        assertFalse(send(WatchMessage.Alarm("r1", "Wake up")))
        assertTrue(diet.executed.isEmpty() && tasks.executed.isEmpty())
        assertEquals(0, refreshes)
    }

    @Test fun aFailingHandlerNeverThrowsOutOfTheListenerService() = runTest {
        val broken = PhoneWearInbox(
            UniversalCaptureService(setOf(object : app.klean.shared.capture.CaptureHandler by RecordingHandler(ModuleId.DIET) {
                override suspend fun execute(input: app.klean.shared.capture.CaptureInput, action: app.klean.shared.capture.CandidateAction): String =
                    throw IllegalStateException("db gone")
            }), gate, TargetRouter()),
            workouts, gate, refresh, SteppingClock(),
        )
        assertFalse(WearCodec.encode(WatchMessage.LogFood("tea")).let { (p, b) -> broken.onMessage(p, b) })
    }
}
