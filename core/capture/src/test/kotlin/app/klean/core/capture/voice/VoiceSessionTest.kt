package app.klean.core.capture.voice

import app.klean.core.capture.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionTest {
    private val rec = FakeVoiceRecognizer()
    private val saved = mutableListOf<String>()
    private val undone = mutableListOf<VoiceUndo>()

    private fun TestScope.session(saveResult: String? = "r1") = VoiceSession(
        scope = backgroundScope,
        recognizer = rec,
        save = { saved += it; saveResult },
        undo = { undone += it },
    )

    @Test fun finalResultSavesOnceAndOffersUndoForFiveSeconds() = runTest {
        val s = session()
        s.onMic()
        assertTrue(s.state.value.listening)
        rec.partial("two man")
        assertEquals("two man", s.state.value.partial)
        rec.final("  two manakish ")
        runCurrent()
        assertEquals(listOf("two manakish"), saved)
        assertFalse(s.state.value.listening)
        assertEquals(VoiceUndo("two manakish", "r1"), s.state.value.undo)
        advanceTimeBy(VoiceSession.UNDO_MS - 100)
        assertEquals(VoiceUndo("two manakish", "r1"), s.state.value.undo)
        advanceTimeBy(200)
        assertNull(s.state.value.undo)
        assertTrue(undone.isEmpty())
    }

    @Test fun undoWithinWindowUndoesTheSave() = runTest {
        val s = session()
        s.onMic()
        rec.final("tea")
        runCurrent()
        s.undoLast()
        runCurrent()
        assertEquals(listOf(VoiceUndo("tea", "r1")), undone)
        assertNull(s.state.value.undo)
        s.undoLast()
        runCurrent()
        assertEquals(1, undone.size)
    }

    @Test fun noRecogniserShowsCalmOfflineMessageWithoutStarting() = runTest {
        rec.mode = VoiceMode.NONE
        val s = session()
        s.onMic()
        assertEquals(0, rec.starts)
        assertFalse(s.state.value.listening)
        assertEquals(R.string.voice_unavailable_offline, s.state.value.messageRes)
    }

    @Test fun offlineErrorShowsTheSameMessage() = runTest {
        val s = session()
        s.onMic()
        rec.error(VoiceError.UNAVAILABLE_OFFLINE)
        assertFalse(s.state.value.listening)
        assertEquals(R.string.voice_unavailable_offline, s.state.value.messageRes)
        assertTrue(saved.isEmpty())
    }

    @Test fun blankFinalSavesNothing() = runTest {
        val s = session()
        s.onMic()
        rec.final("   ")
        runCurrent()
        assertTrue(saved.isEmpty())
        assertEquals(R.string.voice_no_match, s.state.value.messageRes)
    }

    @Test fun micWhileListeningStops() = runTest {
        val s = session()
        s.onMic()
        s.onMic()
        assertEquals(1, rec.starts)
        assertEquals(1, rec.stops)
    }

    @Test fun permissionRationaleThenGrantStarts() = runTest {
        val s = session()
        s.onPermissionRequested()
        assertTrue(s.state.value.askingPermission)
        s.onPermissionResult(granted = true)
        assertFalse(s.state.value.askingPermission)
        assertEquals(1, rec.starts)
    }

    @Test fun permissionDeniedShowsNeutralLine() = runTest {
        val s = session()
        s.onPermissionRequested()
        s.onPermissionResult(granted = false)
        assertEquals(0, rec.starts)
        assertEquals(R.string.voice_permission_off, s.state.value.messageRes)
    }

    @Test fun nothingSavedMeansNoUndoChip() = runTest {
        val s = session(saveResult = null)
        s.onMic()
        rec.final("tea")
        runCurrent()
        assertEquals(listOf("tea"), saved)
        assertNull(s.state.value.undo)
    }

    @Test fun startingAgainClearsOldMessage() = runTest {
        val s = session()
        s.onMic()
        rec.error(VoiceError.NO_MATCH)
        assertEquals(R.string.voice_no_match, s.state.value.messageRes)
        s.onMic()
        assertNull(s.state.value.messageRes)
        assertTrue(s.state.value.listening)
    }

    /** Found on Waydroid: the on-device service failed to bind and no callback ever came. */
    @Test fun recogniserThatNeverGetsReadyEndsCalmly() = runTest {
        val s = session()
        s.onMic()
        advanceTimeBy(VoiceSession.START_TIMEOUT_MS + 1)
        assertFalse(s.state.value.listening)
        assertEquals(R.string.voice_unavailable_offline, s.state.value.messageRes)
        assertEquals(1, rec.cancels)
    }

    @Test fun readyRecogniserKeepsListening() = runTest {
        val s = session()
        s.onMic()
        rec.ready()
        advanceTimeBy(VoiceSession.START_TIMEOUT_MS + 1)
        assertTrue(s.state.value.listening)
        rec.final("tea")
        runCurrent()
        assertEquals(listOf("tea"), saved)
    }

    @Test fun stopWithNoResultStillEndsListening() = runTest {
        val s = session()
        s.onMic()
        rec.ready()
        s.onMic()
        assertTrue(s.state.value.listening)
        advanceTimeBy(VoiceSession.STOP_TIMEOUT_MS + 1)
        assertFalse(s.state.value.listening)
        assertEquals(1, rec.cancels)
    }
}
