package app.klean.feature.diet.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
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
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DietStatusNotifierTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 9), 12 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val gate = SettingsModuleGate(settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val noAi = object : EnrichmentPolicy { override suspend fun hasProviderFor(task: AiTask) = false }
    private val repo = DefaultDietRepository(db, stamper, clock, DubaiZone, settings, DietChangeDispatcher(emptySet()), noAi, scope)
    private val notifier = DietStatusNotifier(ctx, { repo }, gate, settings, AndroidDietFormatter(ctx, DubaiZone))

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun posted(): Notification? = shadowOf(nm).getNotification(NotificationIds.DIET_STATUS)
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test fun emptyDayPostsCalmOngoingStatus() = runBlocking {
        notifier.refresh()
        val n = posted()
        assertNotNull(n)
        n!!
        assertEquals("What did you eat?", n.title())
        assertEquals("Nothing logged yet today", n.text())
        assertEquals(Channels.DIET_STATUS, n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(NotificationCompat.CATEGORY_STATUS, n.category)
        assertNotNull(n.deleteIntent)
        assertNotNull(n.contentIntent)
    }

    @Test fun summaryShowsCountAndLastItemAndPrivatePublicVersion() = runBlocking {
        repo.logEntry("toast", CaptureSource.MODULE_SCREEN, eatenAt = clock.nowMs - 60_000)
        repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        notifier.refresh()
        val n = posted()!!
        assertTrue(n.text(), n.text()!!.startsWith("2 today · last: tea at "))
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("2 entries today", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        // The public version never carries what was eaten.
        assertNull(n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test fun logFoodActionIsAnInlineReplyThatNeedsNoUnlock() = runBlocking {
        notifier.refresh()
        val action = posted()!!.actions.single { it.title.toString() == "Log food" }
        assertEquals(DietReplyReceiver.KEY_TEXT, action.remoteInputs.single().resultKey)
        assertEquals("What did you eat?", action.remoteInputs.single().label.toString())
        assertTrue(!action.isAuthenticationRequired)
        assertTrue(action.allowGeneratedReplies.not())
    }

    @Test fun savedTextIsShownAfterReply() = runBlocking {
        notifier.refresh(savedText = "shawarma and fries")
        assertEquals("Saved: shawarma and fries", posted()!!.text())
    }

    @Test fun cancelledWhenDietDisabled() = runBlocking {
        notifier.refresh()
        assertNotNull(posted())
        gate.setEnabled(ModuleId.DIET, false)
        notifier.refresh()
        assertNull(posted())
    }

    @Test fun cancelledWhenSettingOff() = runBlocking {
        notifier.refresh()
        settings.set(SyncedKeys.DietStatusNotification, false)
        notifier.refresh()
        assertNull(posted())
    }

    @Test fun silentlySkipsWithoutPermission() = runBlocking {
        shadowOf(ctx as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.refresh()
        assertNull(posted())
    }
}
