package app.klean.watch

import app.klean.nav.FakeModuleGate
import app.klean.shared.module.ModuleId
import app.klean.shared.wear.FoodLine
import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WorkoutRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@OptIn(ExperimentalCoroutinesApi::class)
// Robolectric: the code under test logs through android.util.Log.
@RunWith(AndroidJUnit4::class)
class WatchStatePublisherTest {
    private val gate = FakeModuleGate()
    private val layer = FakeWearDataLayer()
    private val clock = SteppingClock(5_000)
    private var snapshot = WatchState(todayFood = listOf(FoodLine(1, "tea")), workouts = listOf(WorkoutRef("tpl-1", "Legs")))
    private var reads = 0

    private fun TestScope.publisher() = WatchStatePublisher(
        source = { reads++; snapshot },
        layer = layer,
        gate = gate,
        clock = clock,
        scope = backgroundScope,
        debounceMs = 2_000,
    ).also { it.start() }

    private fun last(): WatchState = WearCodec.decodeState(layer.puts.last())!!

    @Test fun startPublishesOnceAfterTheDebounce() = runTest {
        publisher()
        runCurrent()
        assertTrue(layer.puts.isEmpty())
        advanceTimeBy(2_001)
        assertEquals(1, layer.puts.size)
        assertEquals("tea", last().todayFood.single().text)
        assertEquals(5_000L, last().generatedAt)
    }

    @Test fun aBurstOfChangesIsOnePut() = runTest {
        val p = publisher()
        advanceTimeBy(2_001)
        snapshot = snapshot.copy(todayFood = snapshot.todayFood + FoodLine(2, "toast"))
        repeat(5) { p.refresh(); advanceTimeBy(300) }
        assertEquals(1, layer.puts.size) // still inside the debounce window
        advanceTimeBy(2_001)
        assertEquals(2, layer.puts.size)
        assertEquals(listOf("tea", "toast"), last().todayFood.map { it.text })
    }

    @Test fun anUnchangedSnapshotIsNotPutAgain() = runTest {
        val p = publisher()
        advanceTimeBy(2_001)
        clock.ms += 60_000
        p.refresh()
        advanceTimeBy(2_001)
        assertEquals(1, layer.puts.size)
        assertEquals(2, reads)
    }

    @Test fun aModuleToggleRepublishes() = runTest {
        publisher()
        advanceTimeBy(2_001)
        snapshot = snapshot.copy(dietOn = false, todayFood = emptyList())
        gate.setEnabled(ModuleId.DIET, false)
        advanceTimeBy(2_001)
        assertEquals(2, layer.puts.size)
        assertFalse(last().dietOn)
    }

    @Test fun aBrokenDataLayerOrSourceNeverStopsThePublisher() = runTest {
        val p = publisher()
        layer.works = false
        advanceTimeBy(2_001)
        assertTrue(layer.puts.isEmpty())
        layer.works = true
        p.refresh()
        advanceTimeBy(2_001)
        assertEquals(1, layer.puts.size)
        val failing = WatchStatePublisher({ throw IllegalStateException("no db") }, layer, gate, clock, backgroundScope, 2_000)
        failing.start()
        failing.refresh()
        advanceTimeBy(5_000)
        assertEquals(1, layer.puts.size)
    }

    @Test fun noPlayServicesMeansNothingRunsAtAll() = runTest {
        val p = WatchStatePublisher({ reads++; snapshot }, NoWearDataLayer, gate, clock, backgroundScope, 2_000)
        p.start()
        p.refresh()
        advanceTimeBy(10_000)
        assertEquals(0, reads)
    }

    @Test fun onceTheDataLayerSaysItHasNoWearableApiNothingIsReadAnyMore() = runTest {
        val p = publisher()
        advanceTimeBy(2_001)
        assertEquals(1, reads)
        layer.available = false
        snapshot = snapshot.copy(dietOn = false)
        p.refresh()
        advanceTimeBy(5_000)
        assertEquals(1, reads)
        assertEquals(1, layer.puts.size)
    }
}
