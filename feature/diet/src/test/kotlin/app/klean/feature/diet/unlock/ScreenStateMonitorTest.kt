package app.klean.feature.diet.unlock

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.time.HOUR_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper

/** The runtime receiver feeds the trigger: SCREEN_OFF stores the time, USER_PRESENT after 3 h posts. */
@RunWith(AndroidJUnit4::class)
class ScreenStateMonitorTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(EngineRig.t(10))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val poster = FakeUnlockPoster()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trigger = UnlockSheetTrigger(
        db, PromptLogWriter(db, stamper, clock), settings, SettingsModuleGate(settings), prefs, clock, DubaiZone, poster,
    )
    private val monitor = ScreenStateMonitor(ctx, trigger, scope)

    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun send(action: String) {
        ctx.sendBroadcast(Intent(action))
        ShadowLooper.idleMainLooper()
        delay(300)
    }

    @Test fun screenOffThenUnlockAfterThreeHoursPostsTheSheet() = runBlocking {
        monitor.start()
        monitor.start() // idempotent: one receiver
        send(Intent.ACTION_SCREEN_OFF)
        assertEquals(EngineRig.t(10), prefs.lastScreenOffAt.first())
        clock.nowMs += 3 * HOUR_MS
        send(Intent.ACTION_SCREEN_ON)
        send(Intent.ACTION_USER_PRESENT)
        assertEquals(1, poster.posted.size)
    }
}
