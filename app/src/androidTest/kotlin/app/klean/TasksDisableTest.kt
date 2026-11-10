package app.klean

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.reminder.ReminderRepository
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.TodayItems
import app.klean.feature.reminders.ReminderFirer
import app.klean.feature.reminders.ReminderScheduler
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TasksRepository
import app.klean.feature.tasks.review.TaskReview
import app.klean.shared.module.ModuleId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * M18: switching Tasks or Reminders off through the real registry takes each off every surface: nav, Today
 * (status line, widget items), capture, the review alarm and notification, and every reminder alarm.
 */
@HiltAndroidTest
class TasksDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var tasks: TasksRepository
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var firer: ReminderFirer
    @Inject lateinit var review: TaskReview
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var status: StatusLine
    @Inject lateinit var todayItems: TodayItems
    @Inject lateinit var clock: Clock

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Before fun setUp() = runBlocking {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        registry.setEnabled(ModuleId.TASKS, true)
        registry.setEnabled(ModuleId.REMINDERS, true)
    }

    @After fun tearDown() = runBlocking {
        registry.setEnabled(ModuleId.TASKS, true)
        registry.setEnabled(ModuleId.REMINDERS, true)
        nm.cancelAll()
    }

    private suspend fun awaitNotification(present: Boolean, match: (android.service.notification.StatusBarNotification) -> Boolean): Boolean {
        repeat(30) {
            if (nm.activeNotifications.any(match) == present) return true
            delay(100)
        }
        return false
    }

    @Test fun disablingTasksRemovesItEverywhere() = runBlocking {
        val todayDate = tasks.today()
        tasks.add(TaskDraft("Tasks disable check", dueDate = todayDate))
        tasks.add(TaskDraft("From earlier", dueDate = LocalDate(2020, 1, 1)))
        review.reschedule()
        assertNotNull("review armed while on", TaskReview.pending(ctx, create = false))
        assertTrue(review.onAlarm(force = true))
        assertTrue(awaitNotification(true) { it.id == TaskReview.NOTIFICATION_ID })
        assertTrue(status.fragments(10).first().any { it.key == "tasks.count" })
        assertTrue(todayItems.items().first().any { it.key.startsWith("task:") })

        registry.setEnabled(ModuleId.TASKS, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.TASKS })
        assertNull("review alarm", TaskReview.pending(ctx, create = false))
        assertTrue("review notification", awaitNotification(false) { it.id == TaskReview.NOTIFICATION_ID })
        assertFalse(review.onAlarm(force = true))
        review.reschedule()
        assertNull("stays off after a reschedule", TaskReview.pending(ctx, create = false))
        assertTrue(status.fragments(10).first().none { it.key == "tasks.count" })
        assertTrue(todayItems.items().first().none { it.key.startsWith("task:") })
        assertTrue(capture.availableHandlers.first().none { it.moduleId == ModuleId.TASKS })
    }

    @Test fun disablingRemindersCancelsEveryAlarm() = runBlocking {
        val now = clock.now().toEpochMilliseconds()
        val ping = reminders.add("Reminders disable check", now + 3_600_000, TimeZone.currentSystemDefault().id, isAlarm = false)
        val alarm = reminders.add("Alarm disable check", now + 7_200_000, TimeZone.currentSystemDefault().id, isAlarm = true)
        assertTrue(scheduler.isArmed(ping))
        assertTrue(scheduler.isArmed(alarm))

        registry.setEnabled(ModuleId.REMINDERS, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.REMINDERS })
        assertFalse(scheduler.isArmed(ping))
        assertFalse(scheduler.isArmed(alarm))
        scheduler.reschedule()
        assertFalse("stays off after a reschedule", scheduler.isArmed(ping))
        assertEquals(ReminderFirer.Result.SKIPPED, firer.fire(ping))
        assertTrue(awaitNotification(false) { it.tag == ping && it.id == AndroidReminderNotifier.NOTIFICATION_ID })
        assertTrue(capture.availableHandlers.first().none { it.moduleId == ModuleId.REMINDERS })
        assertTrue(todayItems.items().first().none { it.key.startsWith("reminder:") })
        // A reminder added while off (from a task page) isn't armed either.
        val later = reminders.add("Added while off", now + 3_600_000, TimeZone.currentSystemDefault().id, isAlarm = false)
        assertFalse(scheduler.isArmed(later))

        registry.setEnabled(ModuleId.REMINDERS, true)
        assertTrue(scheduler.isArmed(ping))
        assertTrue(scheduler.isArmed(alarm))
        listOf(ping, alarm, later).forEach { reminders.delete(it) }
        assertFalse(scheduler.isArmed(ping))
    }
}
