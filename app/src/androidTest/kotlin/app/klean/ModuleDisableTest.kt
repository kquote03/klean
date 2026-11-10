package app.klean

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiProvider
import app.klean.core.ai.AiService
import app.klean.core.ai.Capability
import app.klean.core.ai.StructuredRequest
import app.klean.core.capture.CaptureTileModel
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.core.data.place.PlaceDraft
import app.klean.core.data.place.PlaceRepository
import app.klean.feature.diet.geofence.GeofenceExitHandler
import app.klean.feature.diet.geofence.GeofenceRegistrar
import app.klean.feature.diet.geofence.ProximityAlertBackend
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.NotificationIds
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.DefaultDietEnablement
import app.klean.feature.diet.ai.EnrichmentEnqueuer
import app.klean.feature.diet.ai.EnrichmentScheduler
import app.klean.feature.diet.ai.EnrichmentSweep
import app.klean.feature.diet.ai.WorkManagerEnrichmentScheduler
import app.klean.feature.diet.data.DietChange
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.delivery.DeliveryOffer
import app.klean.feature.diet.delivery.DeliveryPrompter
import app.klean.feature.diet.notification.DietStatusLine
import app.klean.feature.diet.prompt.DayRollover
import app.klean.feature.diet.prompt.DayRolloverReceiver
import app.klean.feature.diet.prompt.DietPrompts
import app.klean.feature.diet.prompt.PromptAlarmReceiver
import app.klean.feature.diet.prompt.PromptAlarms
import app.klean.feature.diet.unlock.UnlockSheetTrigger
import app.klean.feature.diet.widget.dietWidgetStates
import app.klean.shared.ai.AiTask
import app.klean.shared.ai.ProviderId
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
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
 * Task 13.1: switching diet off through the real registry removes it from every surface, including the
 * M10–M12 additions (AI enrichment, unlock sheet, delivery listener), M15 place alerts and the day-rollover alarm.
 */
@HiltAndroidTest
class ModuleDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var gate: ModuleGate
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var prefs: DevicePrefs
    @Inject lateinit var clock: Clock
    @Inject lateinit var status: DietStatusLine
    @Inject lateinit var prompts: DietPrompts
    @Inject lateinit var rollover: DayRollover
    @Inject lateinit var unlock: UnlockSheetTrigger
    @Inject lateinit var delivery: DeliveryPrompter
    @Inject lateinit var repo: DietRepository
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var places: PlaceRepository
    @Inject lateinit var geofences: GeofenceRegistrar
    @Inject lateinit var exits: GeofenceExitHandler
    private var placeId: String? = null

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private lateinit var savedOffer: String

    /** An assigned, enabled provider, so only the module switch can stop enrichment. */
    private val ai = object : AiService {
        val p = object : AiProvider {
            override val id = ProviderId.OPENAI_COMPAT
            override val needsNetwork = true
            override suspend fun capabilities() = emptySet<Capability>()
            override suspend fun isConfiguredAndEnabled() = true
            override suspend fun generate(req: StructuredRequest): AiOutcome = AiOutcome.NoProvider
        }
        override suspend fun hasProviderFor(task: AiTask) = true
        override suspend fun providerFor(task: AiTask) = p
        override suspend fun run(task: AiTask, req: StructuredRequest, sendImage: Boolean) = AiOutcome.NoProvider
        override suspend fun runWith(p: AiProvider, req: StructuredRequest, sendImage: Boolean) = AiOutcome.NoProvider
        override fun provider(id: ProviderId) = p
    }

    @Before fun setUp() = runBlocking {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        // HiltTestApplication skips KleanApp.onCreate; without channels every notify() is dropped.
        ChannelRegistrar.ensure(ctx)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        savedOffer = prefs.deliveryOffer.first()
        prefs.setDeliveryOffer(DeliveryOffer.ACCEPTED)
        gate.setEnabled(ModuleId.DIET, true)
    }

    @After fun tearDown() = runBlocking {
        placeId?.let { places.delete(it) }
        geofences.reschedule()
        prefs.setDeliveryOffer(savedOffer)
        prefs.setUnlockSheetPretend(false)
        registry.setEnabled(ModuleId.DIET, true)
    }

    // The holistic Today status (M16) isn't diet's, but with diet as its only line it goes when diet does.
    private fun dietNotifications() =
        nm.activeNotifications.filter { it.id in DefaultDietEnablement.DIET_NOTIFICATIONS + NotificationIds.STATUS }.map { it.id }

    private fun statusText(n: android.app.Notification) =
        "${n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)} / ${n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)}"

    /**
     * Diet's own notifications are gone. The holistic status stays while other modules are on (since M18–M20 it is
     * no longer diet's alone), but it no longer carries diet's line.
     */
    private suspend fun assertNoDietNotifications() {
        val left = awaitDietNotifications { ids -> ids.all { it == NotificationIds.STATUS } && !statusMentionsDiet() }
        val texts = nm.activeNotifications.filter { it.id in left }.map { "${it.id}: ${statusText(it.notification)}" }
        assertTrue("still showing: $texts", left.all { it == NotificationIds.STATUS } && !statusMentionsDiet())
    }

    /** The status line still shows the diet entry logged in the test ("tea", "1 entry"). */
    private fun statusMentionsDiet() = nm.activeNotifications.filter { it.id == NotificationIds.STATUS }
        .any { statusText(it.notification).let { t -> "tea" in t || "entr" in t } }

    /** notify()/cancel() are queued in system_server, so the active list catches up a moment later. */
    private suspend fun awaitDietNotifications(expect: (List<Int>) -> Boolean): List<Int> {
        var ids = dietNotifications()
        repeat(30) {
            if (expect(ids)) return ids
            delay(100)
            ids = dietNotifications()
        }
        return ids
    }

    private fun pending(code: Int, receiver: Class<*>): PendingIntent? = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, receiver), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun activeEnrichment(): List<WorkInfo> = WorkManager.getInstance(ctx)
        .getWorkInfosByTag(WorkManagerEnrichmentScheduler.TAG).get()
        .filter { !it.state.isFinished }

    @Test fun disablingDietRemovesItEverywhere() = runBlocking {
        // On: status line, check-in alarm, rollover alarm and a queued enrichment all exist.
        status.refresh()
        prompts.reschedule()
        rollover.reschedule()
        val scheduler: EnrichmentScheduler = WorkManagerEnrichmentScheduler(ctx)
        val id = repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        scheduler.enqueue(id, 0L, needsNetwork = true)
        assertTrue("status posted while on", NotificationIds.STATUS in awaitDietNotifications { NotificationIds.STATUS in it })
        assertNotNull("check-in armed while on", pending(PromptAlarms.REQUEST_CODE, PromptAlarmReceiver::class.java))
        assertNotNull("rollover armed while on", pending(DayRollover.REQUEST_CODE, DayRolloverReceiver::class.java))
        assertEquals(1, activeEnrichment().size)
        // M15: a place is being watched.
        val place = places.save(null, PlaceDraft("Test place", 25.1972, 55.2796, 150)).also { placeId = it }
        geofences.reschedule()
        assertNotNull("place watched while on", ProximityAlertBackend.intent(ctx, place, create = false))

        registry.setEnabled(ModuleId.DIET, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.DIET })
        assertNoDietNotifications()
        assertNull("check-in alarm", pending(PromptAlarms.REQUEST_CODE, PromptAlarmReceiver::class.java))
        assertNull("day-rollover alarm", pending(DayRollover.REQUEST_CODE, DayRolloverReceiver::class.java))

        // M10: queued work is dropped and nothing new is queued, by a log or by the sweep.
        assertEquals(emptyList<WorkInfo>(), activeEnrichment())
        EnrichmentEnqueuer(db, ai, scheduler, gate).onChange(DietChange.Logged(id, "tea"))
        assertEquals(0, EnrichmentSweep(db, ai, scheduler, clock, gate).run())
        assertEquals(emptyList<WorkInfo>(), activeEnrichment())

        // M11: even a "3 h break" wake shows no sheet.
        prefs.setUnlockSheetPretend(true)
        assertFalse("unlock sheet", unlock.onWake())

        // M12: an accepted delivery listener asks nothing.
        assertFalse("delivery prompt", delivery.onDelivered("com.talabat"))
        assertNoDietNotifications()

        // M15: no place is watched, and leaving one asks nothing.
        assertNull("place alert", ProximityAlertBackend.intent(ctx, place, create = false))
        assertFalse("place prompt", exits.onTransition(place, entering = false))
        geofences.reschedule()
        assertNull("stays off after a reschedule", ProximityAlertBackend.intent(ctx, place, create = false))

        // Rollover re-arming on its own (e.g. after boot) stays cancelled.
        rollover.reschedule()
        assertNull(pending(DayRollover.REQUEST_CODE, DayRolloverReceiver::class.java))

        // Widget and tile render their disabled states.
        val widget = dietWidgetStates(repo, gate.disabledIds, prefs.highlightPromptId, timeLabel = { "" }).first()
        assertFalse(widget.dietEnabled)
        // The "Add to Klean" tile is holistic (M16): it stays while other modules capture, without diet's handler.
        val handlers = capture.availableHandlers.first().map { it.moduleId }
        assertTrue(handlers.none { it == ModuleId.DIET })
        assertEquals(handlers.isNotEmpty(), CaptureTileModel.from(handlers).available)
    }

    @Test fun reEnablingBringsDietBack() = runBlocking {
        registry.setEnabled(ModuleId.DIET, false)
        registry.setEnabled(ModuleId.DIET, true)
        assertTrue(registry.enabled.first().any { it.id == ModuleId.DIET })
        assertTrue(NotificationIds.STATUS in awaitDietNotifications { NotificationIds.STATUS in it })
        assertNotNull(pending(DayRollover.REQUEST_CODE, DayRolloverReceiver::class.java))
        assertTrue(dietWidgetStates(repo, gate.disabledIds, prefs.highlightPromptId, timeLabel = { "" }).first().dietEnabled)
        assertTrue(CaptureTileModel.from(capture.availableHandlers.first().map { it.moduleId }).available)
    }
}
