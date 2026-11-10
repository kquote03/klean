package app.klean.feature.reminders

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.db.entity.ReminderEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.feature.reminders.notify.ReminderIntents
import app.klean.shared.diet.prompt.PromptFormat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** M27: reminders bridge to the watch with two wrist-sized buttons that act on the phone. */
@RunWith(AndroidJUnit4::class)
class ReminderWearActionsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val fmt = ReminderFormatter(ctx, DubaiZone, FakeClock(0))
    private val r = ReminderEntity("r1", title = "Stretch", fireAt = 0, zone = "Asia/Dubai", sync = SyncColumns(0, 0, "", false, 0))

    @Before fun grant() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun NotificationCompat.WearableExtender.summary() = actions.map { a ->
        a.title.toString() to shadowOf(a.actionIntent).savedIntent.action
    }

    @Test fun aPingOffersDoneAndTenMinutesOnTheWatch() {
        AndroidReminderNotifier(ctx, fmt).ping(r, PromptFormat.STANDARD)
        val n = shadowOf(nm).allNotifications.single()
        assertEquals(
            listOf("Done" to ReminderIntents.DONE, "10 min" to ReminderIntents.SNOOZE_10),
            NotificationCompat.WearableExtender(n).summary(),
        )
        assertEquals(3, NotificationCompat.getActionCount(n)) // the phone keeps Done / 10 min / Later…
    }

    @Test fun aRingingAlarmOffersDismissAndTenMinutesOnTheWatch() {
        val n = AndroidReminderNotifier.alarmNotification(ctx, fmt, r)
        assertEquals(
            listOf("Dismiss" to ReminderIntents.DISMISS, "10 min" to ReminderIntents.SNOOZE_10),
            NotificationCompat.WearableExtender(n).summary(),
        )
    }
}
