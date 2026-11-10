package app.klean.wear.tile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.testing.TestTileClient
import app.klean.wear.clickables
import app.klean.wear.ctx
import app.klean.shared.wear.ActiveWorkout
import app.klean.shared.wear.FoodLine
import app.klean.shared.wear.NextUp
import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WorkoutRef
import app.klean.wear.storeState
import app.klean.wear.texts
import app.klean.wear.ui.SendActivity
import app.klean.wear.ui.WatchCaptureActivity
import java.time.ZoneId
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TileRendererTest {
    private val utc = ZoneId.of("UTC")
    private val state = WatchState(
        generatedAt = 10,
        todayFood = listOf(FoodLine(7 * 3_600_000L, "oats"), FoodLine(9 * 3_600_000L, "coffee"), FoodLine(13 * 3_600_000L, "shawarma"), FoodLine(16 * 3_600_000L, "apple")),
        recentFoods = listOf("coffee", "oats", "tea", "toast", "apple"),
        next = NextUp("REMINDER", "Call Ahmed", at = 17 * 3_600_000L),
        workouts = listOf(WorkoutRef("w1", "Legs"), WorkoutRef("w2", "Push"), WorkoutRef("w3", "Run"), WorkoutRef("w4", "Core")),
    )

    @Test fun quickLogOffersAtMostThreeRecentFoodsAndSpeak() {
        val l = TileLayouts.quickLog(ctx, state)
        assertEquals(listOf("Log food", "coffee", "oats", "tea", "Speak"), texts(l))
        val c = clickables(l)
        assertEquals(SendActivity::class.java.name to mapOf("kind" to "food", "value" to "coffee"), c.getValue("log_food_0"))
        assertEquals(WatchCaptureActivity::class.java.name to mapOf("module" to "diet"), c.getValue("speak"))
    }

    @Test fun todayFoodShowsTheCountAndTheLatestEntries() {
        assertEquals(listOf("Today · 4 entries", "09:00  coffee", "13:00  shawarma", "16:00  apple", "Add"), texts(TileLayouts.todayFood(ctx, state, utc)))
        assertEquals(listOf("Today · 0 entries", "Nothing yet today", "Add"), texts(TileLayouts.todayFood(ctx, WatchState(), utc)))
    }

    @Test fun nextUpShowsTheNextReminderOrACalmEmptyLine() {
        assertEquals(listOf("Next up", "Call Ahmed", "17:00", "Capture"), texts(TileLayouts.nextUp(ctx, state, utc)))
        assertEquals(listOf("Next up", "Nothing coming up", "Capture"), texts(TileLayouts.nextUp(ctx, WatchState(), utc)))
    }

    @Test fun workoutTileStartsASavedWorkoutOrLogsASetDuringOne() {
        val l = TileLayouts.workouts(ctx, state)
        assertEquals(listOf("Start a workout", "Legs", "Push", "Run"), texts(l))
        assertEquals(mapOf("kind" to "workout", "value" to "w2"), clickables(l).getValue("start_w2").second)
        val active = TileLayouts.workouts(ctx, state.copy(active = ActiveWorkout("s1", "Legs", 4)))
        assertEquals(listOf("Legs", "4 sets so far", "Log a set"), texts(active))
    }

    @Test fun foodTilesSayDietIsOffInsteadOfOfferingToLog() {
        val off = state.copy(dietOn = false)
        assertEquals(listOf("Diet is off on your phone"), texts(TileLayouts.quickLog(ctx, off)))
        assertEquals(listOf("Diet is off on your phone"), texts(TileLayouts.todayFood(ctx, off, utc)))
    }

    @Test fun theTileServiceRendersTheStoredState() {
        storeState(state)
        val service = QuickLogTileService()
        val client = TestTileClient(service, Executor { it.run() })
        val tile = settle(client.requestTile(RequestBuilders.TileRequest.Builder().build()))
        val root = tile.tileTimeline!!.timelineEntries.single().layout!!.root
        assertEquals(listOf("Log food", "coffee", "oats", "tea", "Speak"), texts(root))
        assertEquals(KleanTileService.RES_VERSION, tile.resourcesVersion)
        assertTrue(tile.freshnessIntervalMillis > 0)
    }

    @Test fun everyTileServiceRendersWithNoStateYet() {
        listOf(QuickLogTileService::class.java, TodayFoodTileService::class.java, NextUpTileService::class.java, WorkoutTileService::class.java).forEach { cls ->
            val service = cls.getDeclaredConstructor().newInstance()
            val tile = settle(TestTileClient(service, Executor { it.run() }).requestTile(RequestBuilders.TileRequest.Builder().build()))
            assertTrue(cls.simpleName, texts(tile.tileTimeline!!.timelineEntries.single().layout!!.root).isNotEmpty())
        }
    }

    /** The client answers through the main looper, which Robolectric only runs when asked. */
    private fun <T> settle(f: com.google.common.util.concurrent.ListenableFuture<T>): T {
        repeat(500) {
            if (f.isDone) return f.get()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
        return f.get()
    }
}
