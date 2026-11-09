package app.klean.feature.diet.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.KleanDatabase
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.NotificationIds
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Check-in replies (the status line's reply is [StatusReplyReceiverTest]): the real Hilt receiver, the real
 * repository and dispatcher, an in-memory database. No activity is involved.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DietReplyReceiverTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: KleanDatabase

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        hilt.inject()
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun replyIntent(text: String, source: CaptureSource? = CaptureSource.PROMPT_REPLY): Intent {
        val intent = Intent(ctx, DietReplyReceiver::class.java)
        source?.let { intent.putExtra(DietReplyReceiver.EXTRA_SOURCE, it.name) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(DietReplyReceiver.KEY_TEXT).build()),
            intent,
            Bundle().apply { putCharSequence(DietReplyReceiver.KEY_TEXT, text) },
        )
        return intent
    }

    private suspend fun awaitStatus(extra: String, expected: String): String? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var last: String? = null
        repeat(100) {
            last = shadowOf(nm).getNotification(NotificationIds.STATUS)
                ?.extras?.getCharSequence(extra)?.toString()
            if (last == expected) return last
            delay(50)
        }
        return last
    }

    @Test fun promptReplyKeepsItsSourceAndConfirmsOnTheStatusLine() = runBlocking {
        DietReplyReceiver().onReceive(ctx, replyIntent(" tea ", CaptureSource.PROMPT_REPLY))
        val entry = withTimeout(5_000) {
            db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first { it.isNotEmpty() }.single()
        }
        assertEquals("tea", entry.rawText)
        assertEquals(CaptureSource.PROMPT_REPLY, entry.source)
        assertEquals("Saved to Diet", awaitStatus(Notification.EXTRA_SUB_TEXT, "Saved to Diet"))
    }

    @Test fun blankReplySavesNothingButClearsTheSpinner() = runBlocking {
        DietReplyReceiver().onReceive(ctx, replyIntent("   "))
        // The re-post is what clears the reply spinner, so it must still happen.
        assertEquals("Nothing planned. Add anything here.", awaitStatus(Notification.EXTRA_TEXT, "Nothing planned. Add anything here."))
        assertTrue(db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first().isEmpty())
    }
}
