package app.klean.feature.diet.prompt

import app.klean.feature.diet.StatusStack

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.AlarmScheduler
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.notification.PromptHighlight
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.widget.DietWidgetUpdater
import app.klean.shared.ai.AiTask
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class DayRolloverTest {
    private fun t(h: Int, m: Int = 0, day: Int = 9) = atMinute(LocalDate(2026, 11, day), h * 60 + m, Dubai)
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(t(10))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val noAi = object : EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }
    private val repo = DefaultDietRepository(db, stamper, clock, DubaiZone, settings, DietChangeDispatcher(emptySet()), noAi, scope)
    private val status = StatusStack(ctx, repo, gate, settings).line
    private val rollover = DayRollover(ctx, AlarmScheduler(ctx), settings, gate, clock, DubaiZone, status, DietWidgetUpdater(ctx))

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        ShadowAlarmManager.reset()
        scope.cancel()
        db.close()
    }

    private fun rolloverAlarms() = shadowOf(am).scheduledAlarms.filter {
        shadowOf(it.operation).savedIntent.component?.className == DayRolloverReceiver::class.java.name
    }

    @Test fun armsAtTheNextLogicalDayStart() = runBlocking {
        rollover.reschedule()
        // Sleep 00:00–08:00 → the day turns at 04:00.
        assertEquals(t(4, day = 10), rolloverAlarms().single().triggerAtMs)
    }

    @Test fun justAfterMidnightTheSameDayStillTurnsAtFour() = runBlocking {
        clock.nowMs = t(1, day = 10)
        rollover.reschedule()
        assertEquals(t(4, day = 10), rolloverAlarms().single().triggerAtMs)
    }

    @Test fun followsTheSleepWindow() = runBlocking {
        settings.set(SyncedKeys.QuietHoursKey, QuietHours(23 * 60, 7 * 60))
        rollover.reschedule()
        assertEquals(t(3, day = 10), rolloverAlarms().single().triggerAtMs)
    }

    @Test fun rolloverRefreshesTheStatusLineAndReArms() = runBlocking {
        clock.nowMs = t(4, day = 10)
        rollover.onRollover()
        assertNotNull(shadowOf(nm).getNotification(NotificationIds.STATUS))
        assertEquals(t(4, day = 11), rolloverAlarms().single().triggerAtMs)
    }

    @Test fun dietOffCancels() = runBlocking {
        rollover.reschedule()
        gate.setEnabled(ModuleId.DIET, false)
        rollover.reschedule()
        assertEquals(emptyList<Any>(), rolloverAlarms())
        assertNull(shadowOf(nm).getNotification(NotificationIds.STATUS))
    }
}
