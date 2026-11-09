package app.klean.feature.diet.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.db.entity.PromptLogEntity
import app.klean.core.data.db.transact
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.prompt.PromptLogWriter
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.widget.DietWidgetUpdater
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.time.HOUR_MS
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import app.klean.core.capture.status.StatusReplyReceiver
import app.klean.feature.diet.StatusStack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PromptNotifierTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 9), 16 * 60, Dubai))
    private val db = inMemoryDb()
    private val prefs = tempDevicePrefs(clock)
    private val stamper = SyncStamper(db, prefs, clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val noAi = object : EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }
    private val repo = DefaultDietRepository(db, stamper, clock, DubaiZone, settings, DietChangeDispatcher(emptySet()), noAi, scope)
    private val stack = StatusStack(ctx, repo, gate, settings, DefaultPromptHighlight(prefs, db))
    private val status = stack.line
    private val notifier = PromptNotifier(ctx, prefs, status, DietWidgetUpdater(ctx))
    private val writer = PromptLogWriter(db, stamper, clock)

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun prompt(): Notification? = shadowOf(nm).getNotification(NotificationIds.DIET_PROMPT)
    private fun statusText() = shadowOf(nm).getNotification(NotificationIds.STATUS)
        ?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private suspend fun insertPending(id: String, format: PromptFormat, phrasing: String) = db.transact {
        db.promptLogDao().upsert(
            PromptLogEntity(id, "diet", PromptTrigger.CADENCE, null, format, phrasing, clock.nowMs, clock.nowMs, PromptOutcome.PENDING, null, stamper.stamp()),
        )
    }

    @Test fun standardGoesToTheDefaultChannelWithReplyDismissAndTimeout() = runBlocking {
        notifier.present("log-1", PromptFormat.STANDARD, "g3", PromptTrigger.CADENCE)
        val n = prompt()!!
        assertEquals(Channels.DIET_PROMPT, n.channelId)
        assertEquals("Quick food note?", n.title())
        assertEquals(2 * HOUR_MS, n.timeoutAfter)
        val reply = n.actions.single { it.remoteInputs?.isNotEmpty() == true }
        assertEquals(DietReplyReceiver.KEY_TEXT, reply.remoteInputs.single().resultKey)
        val replyIntent = shadowOf(reply.actionIntent).savedIntent
        assertEquals(CaptureSource.PROMPT_REPLY.name, replyIntent.getStringExtra(DietReplyReceiver.EXTRA_SOURCE))
        val dismiss = shadowOf(n.deleteIntent).savedIntent
        assertEquals(PromptDismissReceiver::class.java.name, dismiss.component?.className)
        assertEquals("log-1", dismiss.getStringExtra(PromptDismissReceiver.EXTRA_LOG_ID))
        assertNotNull(n.contentIntent)
    }

    @Test fun promptReplyDoesNotReplaceTheStatusReplyIntent() = runBlocking {
        status.refresh()
        notifier.present("log-1", PromptFormat.STANDARD, "g1", PromptTrigger.CADENCE)
        val statusReply = shadowOf(nm).getNotification(NotificationIds.STATUS)!!.actions.first { it.remoteInputs?.isNotEmpty() == true }
        // The Today line's reply goes to universal capture, never to the check-in receiver.
        assertEquals(StatusReplyReceiver::class.java.name, shadowOf(statusReply.actionIntent).savedIntent.component?.className)
    }

    /** M12: "What did you order?" has its own id (it doesn't replace a check-in) and its own reply source. */
    @Test fun deliveryAsksWhatWasOrderedWithItsOwnIdAndSource() = runBlocking {
        notifier.present("log-d", PromptFormat.HEADS_UP, PromptPhrasings.DELIVERY_ID, PromptTrigger.DELIVERY)
        assertNull(prompt())
        val n = shadowOf(nm).getNotification(NotificationIds.DELIVERY)!!
        assertEquals(Channels.DIET_PROMPT_HEADS_UP, n.channelId)
        assertEquals("What did you order?", n.title())
        val reply = n.actions.single { it.remoteInputs?.isNotEmpty() == true }
        assertEquals(CaptureSource.DELIVERY_PROMPT.name, shadowOf(reply.actionIntent).savedIntent.getStringExtra(DietReplyReceiver.EXTRA_SOURCE))
        assertEquals("log-d", shadowOf(n.deleteIntent).savedIntent.getStringExtra(PromptDismissReceiver.EXTRA_LOG_ID))
        // Logging something takes it away, like a check-in.
        notifier.cancelActive()
        assertNull(shadowOf(nm).getNotification(NotificationIds.DELIVERY))
    }

    /** M15: "Anything from Dubai Mall?" as the user leaves a place, with its own id and reply source. */
    @Test fun leavingAPlaceAsksAboutThatPlace() = runBlocking {
        notifier.present("log-g", PromptFormat.STANDARD, PromptPhrasings.GEOFENCE_ID, PromptTrigger.GEOFENCE, placeLabel = "Dubai Mall")
        assertNull(prompt())
        val n = shadowOf(nm).getNotification(NotificationIds.GEOFENCE)!!
        assertEquals("Anything from Dubai Mall?", n.title())
        val reply = n.actions.single { it.remoteInputs?.isNotEmpty() == true }
        assertEquals(CaptureSource.GEOFENCE_PROMPT.name, shadowOf(reply.actionIntent).savedIntent.getStringExtra(DietReplyReceiver.EXTRA_SOURCE))
        assertEquals("log-g", shadowOf(n.deleteIntent).savedIntent.getStringExtra(PromptDismissReceiver.EXTRA_LOG_ID))
        notifier.cancelActive()
        assertNull(shadowOf(nm).getNotification(NotificationIds.GEOFENCE))
    }

    @Test fun headsUpUsesTheHighChannel() = runBlocking {
        notifier.present("log-2", PromptFormat.HEADS_UP, "g1", PromptTrigger.CADENCE)
        assertEquals(Channels.DIET_PROMPT_HEADS_UP, prompt()!!.channelId)
        @Suppress("DEPRECATION")
        assertEquals(Notification.PRIORITY_HIGH, prompt()!!.priority)
    }

    @Test fun silentVibrateUsesTheVibrateChannel() = runBlocking {
        notifier.present("log-3", PromptFormat.SILENT_VIBRATE, "m_l1", PromptTrigger.LEARNED_MEAL)
        assertEquals(Channels.DIET_PROMPT_VIBRATE, prompt()!!.channelId)
        assertEquals("Anything since lunch?", prompt()!!.title())
    }

    @Test fun widgetHighlightPostsNoNotificationButHighlightsWidgetAndStatus() = runBlocking {
        insertPending("log-4", PromptFormat.WIDGET_HIGHLIGHT, "g5")
        notifier.present("log-4", PromptFormat.WIDGET_HIGHLIGHT, "g5", PromptTrigger.CADENCE)
        assertNull(prompt())
        assertEquals("log-4", prefs.highlightPromptId.first())
        assertEquals("Add a snack or a meal?", statusText())
    }

    @Test fun cancelActiveClearsNotificationAndHighlight() = runBlocking {
        notifier.present("log-5", PromptFormat.STANDARD, "g1", PromptTrigger.CADENCE)
        insertPending("log-6", PromptFormat.WIDGET_HIGHLIGHT, "g2")
        notifier.present("log-6", PromptFormat.WIDGET_HIGHLIGHT, "g2", PromptTrigger.CADENCE)
        notifier.cancelActive()
        assertNull(prompt())
        assertNull(prefs.highlightPromptId.first())
        assertEquals("Nothing planned. Add anything here.", statusText())
    }

    @Test fun dismissMarksOnlyPendingRows() = runBlocking {
        insertPending("log-7", PromptFormat.STANDARD, "g1")
        clock.nowMs += 5 * 60_000
        writer.markDismissed("log-7")
        val row = db.promptLogDao().get("log-7")!!
        assertEquals(PromptOutcome.DISMISSED, row.outcome)
        assertEquals(clock.nowMs, row.outcomeAt)
        // A later dismiss (or a row already resolved) never overwrites the outcome.
        writer.setOutcome("log-7", PromptOutcome.LOGGED, onlyIfPending = false)
        writer.markDismissed("log-7")
        assertEquals(PromptOutcome.LOGGED, db.promptLogDao().get("log-7")!!.outcome)
    }
}
