package app.klean.feature.diet

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.module.SettingsModuleGate
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.notifications.ChannelRegistrar
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.feature.diet.prompt.DietPromptEngine
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.widget.DietWidgetUpdater
import app.klean.shared.ai.AiTask
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Module toggle through the real registry: off clears every diet notification and stops check-ins. */
@RunWith(AndroidJUnit4::class)
class DietEnablementTest {
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
    private val status = DietStatusNotifier(ctx, { repo }, gate, settings, AndroidDietFormatter(ctx, DubaiZone))
    private val calls = mutableListOf<String>()
    private val prompts = object : DietPromptEngine() {
        override suspend fun reschedule() { calls += "reschedule" }
        override suspend fun cancel() { calls += "cancel" }
    }
    private val registry = ModuleRegistry(
        setOf(DietKleanModule(DefaultDietEnablement(ctx, status, prompts, DietWidgetUpdater(ctx)))),
        gate,
    )

    @Before fun setUp() {
        ChannelRegistrar.ensure(ctx)
        shadowOf(ctx as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun post(id: Int) = nm.notify(
        id,
        NotificationCompat.Builder(ctx, Channels.DIET_PROMPT).setSmallIcon(android.R.drawable.ic_dialog_info).build(),
    )

    @Test fun disablingClearsAllDietNotificationsAndStopsCheckIns() = runBlocking {
        status.refresh()
        listOf(NotificationIds.DIET_PROMPT, NotificationIds.UNLOCK, NotificationIds.DELIVERY, NotificationIds.GEOFENCE).forEach(::post)
        post(NotificationIds.BACKUP)
        registry.setEnabled(ModuleId.DIET, false)
        DefaultDietEnablement.DIET_NOTIFICATIONS.forEach { assertNull("id $it", shadowOf(nm).getNotification(it)) }
        // Backups are not diet's to cancel.
        assertNotNull(shadowOf(nm).getNotification(NotificationIds.BACKUP))
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun enablingBringsBackTheStatusAndReschedules() = runBlocking {
        registry.setEnabled(ModuleId.DIET, false)
        calls.clear()
        registry.setEnabled(ModuleId.DIET, true)
        assertNotNull(shadowOf(nm).getNotification(NotificationIds.DIET_STATUS))
        assertEquals(listOf("reschedule"), calls)
    }
}
