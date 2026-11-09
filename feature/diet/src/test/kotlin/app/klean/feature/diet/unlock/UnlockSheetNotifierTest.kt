package app.klean.feature.diet.unlock

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.prompt.EngineRig
import app.klean.shared.diet.CaptureSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class UnlockSheetNotifierTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val repo = FakeDietRepository(FakeClock(EngineRig.t(14)))

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(android.app.Application::class.java.cast(ctx)).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted() = shadowOf(nm).allNotifications.single()

    @Test fun postsAFullScreenSheetWhenAllowed() = runBlocking {
        repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        UnlockSheetNotifier(ctx, { repo }) { true }.post("log1")
        val n = posted()
        assertEquals(Channels.UNLOCK_SHEET, n.channelId)
        assertNotNull(n.fullScreenIntent)
        assertNotNull(n.contentIntent)
        assertEquals(UnlockSheetNotifier.TIMEOUT_MS, n.timeoutAfter)
        assertEquals("Today so far", n.extras.getCharSequence("android.title").toString())
        assertTrue(n.extras.getCharSequence("android.text").toString().contains("1"))
        assertEquals(NotificationIds.UNLOCK, nm.activeNotifications.single().id)
    }

    /** Android 14+ may refuse full-screen intents: fall back to a heads-up whose tap opens the sheet. */
    @Test fun headsUpOnlyWhenFullScreenIsNotAllowed() = runBlocking {
        UnlockSheetNotifier(ctx, { repo }) { false }.post("log1")
        val n = posted()
        assertNull(n.fullScreenIntent)
        assertNotNull(n.contentIntent)
        assertEquals(Channels.UNLOCK_SHEET, n.channelId)
    }
}
