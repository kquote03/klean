package app.klean

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.notifications.QuietHoursService
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.TodayItems
import app.klean.feature.sleep.SleepSurfaces
import app.klean.feature.sleep.data.SleepRepository
import app.klean.feature.sleep.notify.BedtimeReminder
import app.klean.feature.sleep.notify.SleepSessionNotifier
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * M20: switching Sleep off takes its own surfaces away (nav, Today, capture, the bedtime reminder and the
 * while-asleep notification) but quiet hours keep working: the sleep schedule is their source either way.
 */
@HiltAndroidTest
class SleepDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var repo: SleepRepository
    @Inject lateinit var schedule: SleepScheduleRepository
    @Inject lateinit var quiet: QuietHoursService
    @Inject lateinit var bedtime: BedtimeReminder
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var status: StatusLine
    @Inject lateinit var todayItems: TodayItems
    @Inject lateinit var dashboard: DashboardContributions
    @Inject lateinit var clock: Clock

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private var previous: QuietHours = QuietHours.DEFAULT

    @Before fun setUp() = runBlocking {
        hilt.inject()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        previous = schedule.current()
        registry.setEnabled(ModuleId.SLEEP, true)
    }

    @After fun tearDown() = runBlocking {
        schedule.setWindow(previous)
        registry.setEnabled(ModuleId.SLEEP, true)
        nm.cancelAll()
    }

    private suspend fun awaitNotification(present: Boolean, id: Int): Boolean {
        repeat(30) {
            if (nm.activeNotifications.any { it.id == id } == present) return true
            delay(100)
        }
        return false
    }

    @Test fun disablingSleepRemovesItsSurfacesButKeepsQuietHours() = runBlocking {
        bedtime.reschedule()
        assertNotNull("bedtime armed while on", BedtimeReminder.pending(ctx, create = false))
        assertTrue(bedtime.onAlarm(force = true))
        assertTrue(awaitNotification(true, BedtimeReminder.NOTIFICATION_ID))
        val night = repo.goToBed(clock.now().toEpochMilliseconds())
        assertTrue("while-asleep notification", awaitNotification(true, SleepSessionNotifier.NOTIFICATION_ID))
        assertTrue(status.fragments(10).first().any { it.key == SleepSurfaces.KEY_STATUS })
        assertTrue(todayItems.items().first().any { it.key.startsWith("sleep:") })
        assertTrue(dashboard.summaries().first().any { it.key == SleepSurfaces.KEY_TILE })

        registry.setEnabled(ModuleId.SLEEP, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.SLEEP })
        assertTrue(registry.dashboardCards.first().none { it.id == ModuleId.SLEEP })
        assertNull("bedtime alarm", BedtimeReminder.pending(ctx, create = false))
        assertTrue(awaitNotification(false, BedtimeReminder.NOTIFICATION_ID))
        assertTrue(awaitNotification(false, SleepSessionNotifier.NOTIFICATION_ID))
        assertFalse(bedtime.onAlarm(force = true))
        bedtime.reschedule()
        assertNull("stays off after a reschedule", BedtimeReminder.pending(ctx, create = false))
        assertTrue(status.fragments(10).first().none { it.key == SleepSurfaces.KEY_STATUS })
        assertTrue(todayItems.items().first().none { it.key.startsWith("sleep:") })
        assertTrue(capture.availableHandlers.first().none { it.moduleId == ModuleId.SLEEP })

        // Quiet hours still come from the schedule.
        schedule.setWindow(QuietHours(1 * 60, 2 * 60))
        assertEquals(QuietHours(1 * 60, 2 * 60), quiet.quietHours.first())

        repo.delete(night)
    }
}
