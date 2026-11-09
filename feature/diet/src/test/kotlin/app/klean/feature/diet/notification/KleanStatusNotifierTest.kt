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
import app.klean.core.capture.status.StatusReplyReceiver
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusLineProvider
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.StatusStack
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The holistic "Today" status notification, with diet's real line and an optional second module. */
@RunWith(AndroidJUnit4::class)
class KleanStatusNotifierTest {
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
    private val taskLine = MutableStateFlow(listOf(StatusFragment("tasks.count", "2 tasks", 5)))
    private val tasks = object : StatusLineProvider {
        override val moduleId = ModuleId.TASKS
        override fun fragments() = taskLine
    }

    private fun stack(withTasks: Boolean = false) = StatusStack(ctx, repo, gate, settings, others = if (withTasks) setOf(tasks) else emptySet())

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun posted(): Notification? = shadowOf(nm).getNotification(NotificationIds.STATUS)
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun Notification.sub() = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

    @Test fun emptyDayPostsCalmOngoingTodayStatus() = runBlocking {
        stack().notifier.refresh()
        val n = posted()!!
        assertEquals("Today", n.title())
        assertEquals("Nothing planned. Add anything here.", n.text())
        assertEquals(Channels.STATUS, n.channelId)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(NotificationCompat.CATEGORY_STATUS, n.category)
        assertNotNull(n.deleteIntent)
        assertEquals("klean://today", shadowOf(n.contentIntent).savedIntent.dataString)
    }

    @Test fun dietAloneShowsCountAndLastEntry() = runBlocking {
        repo.logEntry("toast", CaptureSource.MODULE_SCREEN, eatenAt = clock.nowMs - 60_000)
        repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        stack().notifier.refresh()
        val text = posted()!!.text()!!
        assertTrue(text, text.startsWith("2 food entries · last: tea "))
    }

    @Test fun textJoinsFragmentsHighestPriorityFirst() = runBlocking {
        repo.logEntry("shawarma", CaptureSource.MODULE_SCREEN)
        stack(withTasks = true).notifier.refresh()
        // With another module, diet keeps to its count.
        assertEquals("2 tasks · 1 food entry", posted()!!.text())
    }

    @Test fun publicVersionSaysHowManyButNeverWhat() = runBlocking {
        repo.logEntry("shawarma", CaptureSource.MODULE_SCREEN)
        stack(withTasks = true).notifier.refresh()
        val n = posted()!!
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertEquals("Today · 2 items", n.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(n.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test fun addSomethingIsOneInlineReplyThatNeedsNoUnlock() = runBlocking {
        stack().notifier.refresh()
        val action = posted()!!.actions.single()
        assertEquals("Add something", action.title.toString())
        assertEquals(StatusReplyReceiver.KEY_TEXT, action.remoteInputs.single().resultKey)
        assertTrue(!action.isAuthenticationRequired)
        assertTrue(action.allowGeneratedReplies.not())
        assertEquals(StatusReplyReceiver::class.java.name, shadowOf(action.actionIntent).savedIntent.component?.className)
    }

    @Test fun savedToStaysUntilTheLineChanges() = runBlocking {
        val s = stack()
        repo.logEntry("toast", CaptureSource.NOTIFICATION_REPLY)
        s.notifier.refresh(savedTo = "Diet")
        assertEquals("Saved to Diet", posted()!!.sub())
        s.notifier.refresh()
        assertEquals("Saved to Diet", posted()!!.sub())
        repo.logEntry("tea", CaptureSource.MODULE_SCREEN)
        s.notifier.refresh()
        assertNull(posted()!!.sub())
    }

    @Test fun disabledModulesDropOutAndNoLineMeansNoNotification() = runBlocking {
        repo.logEntry("toast", CaptureSource.MODULE_SCREEN)
        val s = stack(withTasks = true)
        gate.setEnabled(ModuleId.TASKS, false)
        s.notifier.refresh()
        assertTrue(posted()!!.text()!!.startsWith("1 food entry · last: toast"))
        gate.setEnabled(ModuleId.DIET, false)
        s.notifier.refresh()
        assertNull(posted())
    }

    @Test fun highlightedCheckInLeadsTheLine() = runBlocking {
        val s = StatusStack(ctx, repo, gate, settings, highlight = { PromptPhrasings.generic.first() })
        s.notifier.refresh()
        assertEquals(ctx.getString(PromptPhrasings.generic.first().res), posted()!!.text())
    }

    @Test fun cancelledWhenSettingOff() = runBlocking {
        val s = stack()
        s.notifier.refresh()
        settings.set(SyncedKeys.DietStatusNotification, false)
        s.notifier.refresh()
        assertNull(posted())
    }

    @Test fun silentlySkipsWithoutPermission() = runBlocking {
        shadowOf(ctx as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        stack().notifier.refresh()
        assertNull(posted())
    }
}
