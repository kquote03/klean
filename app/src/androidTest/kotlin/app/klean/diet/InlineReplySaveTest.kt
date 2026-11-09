package app.klean.diet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.klean.core.data.db.KleanDatabase
import app.klean.core.capture.status.StatusReplyReceiver
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Inline reply saves through a real broadcast, within 5 s, without bringing up any activity. */
@HiltAndroidTest
class InlineReplySaveTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: KleanDatabase

    @Before fun setUp() = hilt.inject()

    @Test fun replySavesEntryWithoutLaunchingUi() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // The Today status line's "Add something": universal capture, plain text falls back to diet.
        val intent = Intent(ctx, StatusReplyReceiver::class.java)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(StatusReplyReceiver.KEY_TEXT).build()),
            intent,
            Bundle().apply { putCharSequence(StatusReplyReceiver.KEY_TEXT, "shawarma and fries") },
        )
        val started = System.currentTimeMillis()
        ctx.sendBroadcast(intent)
        val entry = withTimeout(5_000) {
            db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first { it.isNotEmpty() }.single()
        }
        assertEquals("shawarma and fries", entry.rawText)
        assertEquals(CaptureSource.NOTIFICATION_REPLY, entry.source)
        assertTrue(System.currentTimeMillis() - started < 5_000)
        var resumed = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).size
        }
        assertEquals(0, resumed)
    }
}
