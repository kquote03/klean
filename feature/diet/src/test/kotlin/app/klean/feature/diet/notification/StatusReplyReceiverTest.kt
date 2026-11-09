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
import app.klean.core.capture.status.StatusReplyReceiver
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric stand-in for the on-device inline-reply check of the Today status line: the real Hilt
 * receiver, universal capture with the real diet handler, an in-memory database. No activity starts.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class StatusReplyReceiverTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: KleanDatabase

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        hilt.inject()
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun replyIntent(text: String): Intent {
        val intent = Intent(ctx, StatusReplyReceiver::class.java)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(StatusReplyReceiver.KEY_TEXT).build()),
            intent,
            Bundle().apply { putCharSequence(StatusReplyReceiver.KEY_TEXT, text) },
        )
        return intent
    }

    private suspend fun awaitSubText(expected: String): String? {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var last: String? = null
        repeat(100) {
            last = shadowOf(nm).getNotification(NotificationIds.STATUS)
                ?.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            if (last == expected) return last
            delay(50)
        }
        return last
    }

    @Test fun plainTextFallsBackToDietWithNotificationSourceAndConfirms() = runBlocking {
        StatusReplyReceiver().onReceive(ctx, replyIntent("  shawarma and fries "))
        val entry = withTimeout(5_000) {
            db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first { it.isNotEmpty() }.single()
        }
        assertEquals("shawarma and fries", entry.rawText)
        assertEquals(CaptureSource.NOTIFICATION_REPLY, entry.source)
        assertEquals("Saved to Diet", awaitSubText("Saved to Diet"))
        assertNull("no activity is started", shadowOf(ctx as Application).nextStartedActivity)
    }

    @Test fun blankReplySavesNothingButRepostsTheLine() = runBlocking {
        StatusReplyReceiver().onReceive(ctx, replyIntent("   "))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var posted: Notification? = null
        repeat(100) { if (posted == null) { posted = shadowOf(nm).getNotification(NotificationIds.STATUS); delay(50) } }
        assertEquals("Today", posted?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue(db.foodEntryDao().observeRange(0, Long.MAX_VALUE).first().isEmpty())
    }
}
