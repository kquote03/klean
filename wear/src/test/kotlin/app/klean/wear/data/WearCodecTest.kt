package app.klean.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCodecTest {
    @Test fun everyMessageRoundTripsOnItsOwnPath() {
        val all = listOf(
            WatchMessage.Capture("call the bank tomorrow", module = null, viaVoice = true),
            WatchMessage.Capture("2 eggs", module = "diet"),
            WatchMessage.LogFood("oat latte"),
            WatchMessage.StartWorkout("tpl-1"),
            WatchMessage.LogSet("s1", "Squat", 10, 40.0),
            WatchMessage.Alarm("r1", "Wake up"),
        )
        val paths = all.map { m ->
            val (path, bytes) = WearCodec.encode(m)
            assertEquals(m, WearCodec.decode(path, bytes))
            path
        }
        assertEquals(
            listOf(WearPaths.CAPTURE, WearPaths.CAPTURE, WearPaths.LOG_FOOD, WearPaths.START_WORKOUT, WearPaths.LOG_SET, WearPaths.ALARM),
            paths,
        )
        assertTrue(paths.all { it.startsWith(WearPaths.PREFIX) })
    }

    @Test fun aMessageOnTheWrongPathOrGarbageIsIgnored() {
        val (_, bytes) = WearCodec.encode(WatchMessage.LogFood("tea"))
        assertNull(WearCodec.decode(WearPaths.CAPTURE, bytes))
        assertNull(WearCodec.decode(WearPaths.LOG_FOOD, "not json".encodeToByteArray()))
        assertNull(WearCodec.decode(WearPaths.LOG_FOOD, """{"t":"teleport","x":1}""".encodeToByteArray()))
        assertNull(WearCodec.decode(WearPaths.CAPTURE, WearCodec.encode(WatchMessage.Capture("   ")).second))
    }

    @Test fun unknownFieldsFromANewerPhoneAreIgnored() {
        val bytes = """{"t":"log_food","text":"tea","mood":"calm"}""".encodeToByteArray()
        assertEquals(WatchMessage.LogFood("tea"), WearCodec.decode(WearPaths.LOG_FOOD, bytes))
        val state = """{"v":1,"generatedAt":5,"recentFoods":["tea"],"weather":"sunny"}""".encodeToByteArray()
        assertEquals(listOf("tea"), WearCodec.decodeState(state)!!.recentFoods)
    }

    @Test fun aStateFromANewerProtocolIsNotShownHalfUnderstood() {
        assertNull(WearCodec.decodeState("""{"v":2,"generatedAt":5}""".encodeToByteArray()))
    }

    @Test fun textIsTrimmedAndCapped() {
        val (path, bytes) = WearCodec.encode(WatchMessage.Capture("  " + "a".repeat(800)))
        assertEquals(500, (WearCodec.decode(path, bytes) as WatchMessage.Capture).text.length)
    }

    @Test fun aHugeStateIsTrimmedOldestFoodFirstToFitTheDataLayer() {
        val food = (0 until 3_000).map { FoodLine(it.toLong(), "entry number $it with some words") }
        val s = WatchState(generatedAt = 1, todayFood = food, recentFoods = listOf("tea", "toast"), next = NextUp("TASK", "Pay rent"))
        val bytes = WearCodec.encodeState(s)
        assertTrue(bytes.size <= WearCodec.MAX_BYTES)
        val back = WearCodec.decodeState(bytes)!!
        assertTrue(back.todayFood.size in 1 until food.size)
        assertEquals(food.last(), back.todayFood.last())
        assertEquals(listOf("tea", "toast"), back.recentFoods)
        assertEquals("Pay rent", back.next!!.title)
    }
}
