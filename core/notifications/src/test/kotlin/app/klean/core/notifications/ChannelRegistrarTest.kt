package app.klean.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelRegistrarTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Test fun createsAllChannelsWithPlannedImportance() {
        ChannelRegistrar.ensure(ctx)
        val byId = nm.notificationChannels.associateBy { it.id }
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, byId.getValue(Channels.STATUS).importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, byId.getValue(Channels.DIET_PROMPT).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, byId.getValue(Channels.DIET_PROMPT_HEADS_UP).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, byId.getValue(Channels.UNLOCK_SHEET).importance)
        assertEquals("Today", byId.getValue(Channels.STATUS).name)
        assertEquals("Backups", byId.getValue(Channels.BACKUP).name)
        assertEquals(6, byId.size)
    }

    @Test fun vibrateChannelIsSilentWithShortPattern() {
        ChannelRegistrar.ensure(ctx)
        val c = nm.getNotificationChannel(Channels.DIET_PROMPT_VIBRATE)
        assertNull(c.sound)
        assertTrue(c.shouldVibrate())
        assertArrayEquals(longArrayOf(0, 80, 120, 80), c.vibrationPattern)
    }

    /**
     * Pixel hides IMPORTANCE_LOW ("silent") notifications from the lock screen by default, so the
     * status line uses DEFAULT importance and makes itself silent at the channel level.
     */
    @Test fun statusChannelShowsOnLockScreenButNeverMakesNoise() {
        ChannelRegistrar.ensure(ctx)
        val c = nm.getNotificationChannel(Channels.STATUS)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, c.importance)
        assertNull(c.sound)
        assertFalse(c.shouldVibrate())
        assertFalse(c.canShowBadge())
    }

    @Test fun retiredChannelsAreDeleted() {
        nm.createNotificationChannel(NotificationChannel("diet_status", "old", NotificationManager.IMPORTANCE_LOW))
        ChannelRegistrar.ensure(ctx)
        assertNull(nm.getNotificationChannel("diet_status"))
    }

    @Test fun dietStatusV2IsRetiredForTheHolisticChannel() {
        nm.createNotificationChannel(NotificationChannel("diet_status_v2", "old", NotificationManager.IMPORTANCE_DEFAULT))
        ChannelRegistrar.ensure(ctx)
        assertNull(nm.getNotificationChannel("diet_status_v2"))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel(Channels.STATUS).importance)
    }

    @Test fun ensureIsIdempotent() {
        ChannelRegistrar.ensure(ctx)
        ChannelRegistrar.ensure(ctx)
        assertEquals(6, nm.notificationChannels.size)
    }
}
