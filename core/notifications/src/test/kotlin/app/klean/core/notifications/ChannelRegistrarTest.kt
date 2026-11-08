package app.klean.core.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        assertEquals(NotificationManager.IMPORTANCE_LOW, byId.getValue(Channels.DIET_STATUS).importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, byId.getValue(Channels.DIET_PROMPT).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, byId.getValue(Channels.DIET_PROMPT_HEADS_UP).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, byId.getValue(Channels.UNLOCK_SHEET).importance)
        assertEquals("Food status", byId.getValue(Channels.DIET_STATUS).name)
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

    @Test fun ensureIsIdempotent() {
        ChannelRegistrar.ensure(ctx)
        ChannelRegistrar.ensure(ctx)
        assertEquals(6, nm.notificationChannels.size)
    }
}
