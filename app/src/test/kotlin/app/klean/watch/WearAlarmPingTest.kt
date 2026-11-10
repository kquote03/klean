package app.klean.watch

import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WearPaths
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** An alarm ringing on the main phone buzzes the watch: `/klean/alarm` to every connected node. */
// Robolectric: the code under test logs through android.util.Log.
@RunWith(AndroidJUnit4::class)
class WearAlarmPingTest {
    private val layer = FakeWearDataLayer()

    private fun TestScope.ping(l: WearDataLayer = layer) = WearAlarmPing(l, backgroundScope)

    @Test fun ringingSendsTheAlarmMessage() = runTest {
        ping().onAlarm("r1", "Wake up", ringing = true)
        runCurrent()
        val (path, bytes) = layer.messages.single()
        assertEquals(WearPaths.ALARM, path)
        assertEquals(WatchMessage.Alarm("r1", "Wake up", ringing = true), WearCodec.decode(path, bytes))
    }

    @Test fun stoppingSendsRingingFalse() = runTest {
        ping().onAlarm("r1", "Wake up", ringing = false)
        runCurrent()
        assertEquals(WatchMessage.Alarm("r1", "Wake up", ringing = false), layer.messages.single().let { WearCodec.decode(it.first, it.second) })
    }

    @Test fun noDataLayerOrAFailureIsSilent() = runTest {
        ping(NoWearDataLayer).onAlarm("r1", "Wake up", ringing = true)
        layer.works = false
        ping().onAlarm("r1", "Wake up", ringing = true)
        runCurrent()
        assertTrue(layer.messages.isEmpty())
    }
}
