package app.klean.feature.diet.delivery

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.QuietHoursService
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.prompt.FakePresenter
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.module.ModuleId
import app.klean.shared.time.MINUTE_MS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Task 12.1: what a matched delivery notification leads to. Nov 9 2026 Asia/Dubai, quiet 00:00–08:00. */
@RunWith(AndroidJUnit4::class)
class DeliveryPrompterTest {
    private val clock = FakeClock(EngineRig.t(13))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val presenter = FakePresenter()
    private val prompter = DeliveryPrompter(
        db, PromptLogWriter(db, stamper, clock), gate, QuietHoursService(settings, DubaiZone, clock), prefs, clock, presenter,
    )

    @After fun tearDown() = db.close()

    private suspend fun accepted() = prefs.setDeliveryOffer(DeliveryOffer.ACCEPTED)

    @Test fun storesOnlyThatADeliveryHappenedAndAsks() = runBlocking {
        accepted()
        assertTrue(prompter.onDelivered("com.talabat"))
        val row = db.promptLogDao().recent(10).single()
        assertEquals(PromptTrigger.DELIVERY, row.trigger)
        assertEquals("com.talabat", row.triggerDetail)
        assertEquals(PromptFormat.HEADS_UP, row.format)
        assertEquals(PromptOutcome.PENDING, row.outcome)
        assertEquals(ModuleId.DIET.value, row.moduleId)
        assertEquals(EngineRig.t(13), row.sentAt)
        val shown = presenter.shown.single()
        assertEquals(row.id, shown.logId)
        assertEquals(PromptTrigger.DELIVERY, shown.trigger)
        assertEquals(PromptFormat.HEADS_UP, shown.format)
    }

    @Test fun oncePerPackagePer45Minutes() = runBlocking {
        accepted()
        assertTrue(prompter.onDelivered("com.talabat"))
        clock.nowMs += 44 * MINUTE_MS
        assertFalse(prompter.onDelivered("com.talabat"))
        // Another app is its own delivery.
        assertTrue(prompter.onDelivered("com.deliveroo.orderapp"))
        clock.nowMs += 1 * MINUTE_MS
        assertTrue(prompter.onDelivered("com.talabat"))
        assertEquals(3, presenter.shown.size)
    }

    @Test fun nothingUntilTheUserSaidYes() = runBlocking {
        assertFalse(prompter.onDelivered("com.talabat"))
        prefs.setDeliveryOffer(DeliveryOffer.DECLINED)
        assertFalse(prompter.onDelivered("com.talabat"))
        assertTrue(presenter.shown.isEmpty())
        assertTrue(db.promptLogDao().recent(10).isEmpty())
    }

    @Test fun nothingInQuietHours() = runBlocking {
        accepted()
        clock.nowMs = EngineRig.t(2)
        assertFalse(prompter.onDelivered("com.talabat"))
        assertTrue(db.promptLogDao().recent(10).isEmpty())
    }

    @Test fun nothingWhenDietIsOff() = runBlocking {
        accepted()
        gate.setEnabled(ModuleId.DIET, false)
        assertFalse(prompter.onDelivered("com.talabat"))
        assertTrue(presenter.shown.isEmpty())
    }
}
