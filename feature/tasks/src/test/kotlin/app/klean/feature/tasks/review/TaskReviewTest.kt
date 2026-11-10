package app.klean.feature.tasks.review

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.QuietHoursService
import app.klean.feature.tasks.Dubai
import app.klean.feature.tasks.DubaiZone
import app.klean.feature.tasks.FakeClock
import app.klean.feature.tasks.FakeGate
import app.klean.feature.tasks.data.DefaultTasksRepository
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TasksKeys
import app.klean.feature.tasks.inMemoryDb
import app.klean.feature.tasks.tempDevicePrefs
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TaskReviewTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 10 * 60, Dubai))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = FakeGate()
    private val repo = DefaultTasksRepository(db, stamper, clock, DubaiZone, settings, emptySet())
    private val review = TaskReview(
        ctx, repo, settings, gate, prefs, QuietHoursService(settings, DubaiZone, clock), AlarmScheduler(ctx), clock, DubaiZone,
    )

    @Before fun grant() = shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    @After fun close() = db.close()

    private fun posted() = shadowOf(nm).allNotifications.firstOrNull { it.channelId == TaskReview.CHANNEL }

    // Pure.
    @Test fun atMostOnceADay() {
        assertTrue(ReviewPolicy.reviewNeeded(2, null, nov9))
        assertTrue(ReviewPolicy.reviewNeeded(2, LocalDate(2026, 11, 8), nov9))
        assertFalse(ReviewPolicy.reviewNeeded(2, nov9, nov9))
        assertFalse(ReviewPolicy.reviewNeeded(0, null, nov9))
    }

    @Test fun nextTimeIsTodayOrTomorrowOutsideQuietHours() {
        val now = atMinute(nov9, 9 * 60, Dubai)
        assertEquals(atMinute(nov9, 10 * 60, Dubai), ReviewPolicy.nextAt(now, 600, Dubai) { it })
        assertEquals(atMinute(LocalDate(2026, 11, 10), 10 * 60, Dubai), ReviewPolicy.nextAt(now + 2 * 3_600_000, 600, Dubai) { it })
        // A review set inside quiet hours moves to their end.
        val eight = atMinute(LocalDate(2026, 11, 10), 8 * 60, Dubai)
        assertEquals(eight, ReviewPolicy.nextAt(now, 2 * 60, Dubai) { if (it < eight) eight else it })
    }

    // Wired.
    @Test fun postsOnceWithACalmCount() = runBlocking {
        repo.add(TaskDraft("a", dueDate = LocalDate(2026, 11, 7)))
        repo.add(TaskDraft("b", dueDate = LocalDate(2026, 11, 8)))
        repo.add(TaskDraft("today", dueDate = nov9))
        assertTrue(review.onAlarm())
        val n = posted()!!
        assertEquals("Still want these?", n.extras.getString("android.title"))
        assertEquals("2 tasks from earlier days", n.extras.getString("android.text"))
        assertEquals("2026-11-09", settings.get(TasksKeys.ReviewShownOn))
        nm.cancelAll()
        assertFalse("not twice a day", review.onAlarm())
        assertNull(posted())
        // The debug broadcast shows it anyway.
        assertTrue(review.onAlarm(force = true))
    }

    @Test fun nothingFromEarlierDaysNothingShown() = runBlocking {
        repo.add(TaskDraft("today", dueDate = nov9))
        assertFalse(review.onAlarm(force = true))
        assertNull(posted())
    }

    @Test fun onlyOnTheMainPhoneAndWhileTasksIsOn() = runBlocking {
        repo.add(TaskDraft("a", dueDate = LocalDate(2026, 11, 7)))
        prefs.setIsMainPhone(false)
        assertFalse(review.onAlarm())
        prefs.setIsMainPhone(true)
        gate.setEnabled(ModuleId.TASKS, false)
        assertFalse(review.onAlarm(force = true))
        assertNull(posted())
    }

    @Test fun alarmIsArmedWhileOnAndCancelledWhenOff() = runBlocking {
        review.reschedule()
        val alarm = shadowOf(am).scheduledAlarms.single { shadowOf(it.operation).savedIntent.action == TaskReview.ACTION_REVIEW }
        assertEquals(atMinute(LocalDate(2026, 11, 10), 10 * 60, Dubai), alarm.triggerAtMs)
        gate.setEnabled(ModuleId.TASKS, false)
        review.reschedule()
        assertNull(TaskReview.pending(ctx, create = false))
        gate.setEnabled(ModuleId.TASKS, true)
        settings.set(TasksKeys.ReviewEnabled, false)
        review.reschedule()
        assertNull(TaskReview.pending(ctx, create = false))
    }
}
