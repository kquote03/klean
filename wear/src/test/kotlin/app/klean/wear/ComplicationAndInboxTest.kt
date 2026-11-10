package app.klean.wear

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import app.klean.wear.complication.FoodLogComplicationService
import app.klean.shared.wear.FoodLine
import app.klean.wear.data.WatchGraph
import app.klean.wear.data.WatchInbox
import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WearPaths
import app.klean.wear.tile.WearColors
import app.klean.wear.ui.SendActivity
import app.klean.wear.ui.WatchCaptureActivity
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class ComplicationAndInboxTest {
    private val default = WatchGraph.link

    @After fun restore() {
        WatchGraph.link = default
    }

    private fun complication(): ShortTextComplicationData? = runTestResult {
        val s = Robolectric.buildService(FoodLogComplicationService::class.java).create().get()
        s.onComplicationRequest(ComplicationRequest(1, ComplicationType.SHORT_TEXT, false)) as ShortTextComplicationData?
    }

    private fun <T> runTestResult(block: suspend () -> T): T {
        var out: Result<T>? = null
        runTest { out = runCatching { block() } }
        return out!!.getOrThrow()
    }

    @Test fun complicationShowsTodaysFoodCountAndOpensFoodCapture() {
        storeState(WatchState(generatedAt = 1, todayFood = listOf(FoodLine(1, "oats"), FoodLine(2, "tea"))))
        val c = complication()!!
        assertEquals("2", c.text.getTextAt(ctx.resources, java.time.Instant.EPOCH).toString())
        assertEquals("2 food entries today. Tap to log.", c.contentDescription!!.getTextAt(ctx.resources, java.time.Instant.EPOCH).toString())
        val tap = shadowOf(c.tapAction).savedIntent
        assertEquals(WatchCaptureActivity::class.java.name, tap.component!!.className)
        assertEquals("diet", tap.getStringExtra(WatchCaptureActivity.EXTRA_MODULE))
    }

    @Test fun complicationWithDietOffIsAPlainCaptureShortcut() {
        storeState(WatchState(generatedAt = 1, dietOn = false))
        val c = complication()!!
        assertEquals("Klean", c.text.getTextAt(ctx.resources, java.time.Instant.EPOCH).toString())
        assertNull(shadowOf(c.tapAction).savedIntent.getStringExtra(WatchCaptureActivity.EXTRA_MODULE))
    }

    @Test fun complicationAnswersOnlyShortText() {
        val s = Robolectric.buildService(FoodLogComplicationService::class.java).create().get()
        runTest { assertNull(s.onComplicationRequest(ComplicationRequest(1, ComplicationType.LONG_TEXT, false))) }
        assertNotNull(s.getPreviewData(ComplicationType.SHORT_TEXT))
    }

    @Test fun aNewStateIsStoredAndALateOlderOneIgnored() {
        assertTrue(WatchInbox.onState(ctx, WearCodec.encodeState(WatchState(generatedAt = 20, recentFoods = listOf("tea")))))
        assertFalse(WatchInbox.onState(ctx, WearCodec.encodeState(WatchState(generatedAt = 10, recentFoods = listOf("old")))))
        assertFalse(WatchInbox.onState(ctx, "garbage".encodeToByteArray()))
        assertEquals(listOf("tea"), WatchGraph.store(ctx).load().recentFoods)
    }

    @Test fun onlyTheAlarmPingIsAcceptedFromThePhone() {
        val (path, bytes) = WearCodec.encode(WatchMessage.Alarm("r1", "Wake up"))
        assertEquals(WatchMessage.Alarm("r1", "Wake up"), WatchInbox.onMessage(ctx, path, bytes))
        val (p2, b2) = WearCodec.encode(WatchMessage.LogFood("tea"))
        assertNull(WatchInbox.onMessage(ctx, p2, b2))
        assertEquals(WearPaths.ALARM, path)
    }

    @Test fun chipTapsAndVoiceBecomeTheRightMessages() {
        assertEquals(WatchMessage.LogFood("tea"), SendActivity.messageFor(SendActivity.KIND_FOOD, " tea "))
        assertEquals(WatchMessage.StartWorkout("w1"), SendActivity.messageFor(SendActivity.KIND_WORKOUT, "w1"))
        assertNull(SendActivity.messageFor("other", "x"))
        assertNull(SendActivity.messageFor(SendActivity.KIND_FOOD, "  "))
        assertEquals(WatchMessage.LogFood("2 eggs"), WatchCaptureActivity.captureMessage("2 eggs", "diet"))
        assertEquals(WatchMessage.Capture("call mum at 5", null, viaVoice = true), WatchCaptureActivity.captureMessage("call mum at 5", null))
    }

    @Test fun aChipTapSendsToThePhoneAndSaysSoCalmly() {
        val link = RecordingLink()
        WatchGraph.link = { link }
        val intent = android.content.Intent(ctx, SendActivity::class.java)
            .putExtra(SendActivity.EXTRA_KIND, SendActivity.KIND_FOOD).putExtra(SendActivity.EXTRA_VALUE, "oat latte")
        Robolectric.buildActivity(SendActivity::class.java, intent).create()
        repeat(20) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        assertEquals(listOf<WatchMessage>(WatchMessage.LogFood("oat latte")), link.sent)
        assertEquals("Sent to your phone", ShadowToast.getTextOfLatestToast())
    }

    @Test fun colorsMatchThePhonesDarkTokens() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "core/design/tokens").isDirectory }
        val dark = Json.parseToJsonElement(File(root, "core/design/tokens/klean-color-tokens.json").readText()).jsonObject.getValue("dark").jsonObject
        WearColors.byToken.forEach { (token, value) ->
            val hex = dark.getValue(token).jsonPrimitive.content.removePrefix("#")
            assertEquals(token, ("FF$hex").toLong(16).toInt(), value)
        }
    }
}
