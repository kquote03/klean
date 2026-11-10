package app.klean

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.TodayItems
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.calendar.mirror.AndroidCalendarProvider
import app.klean.feature.calendar.mirror.MirrorHook
import app.klean.feature.calendar.reminder.EventReminderSync
import app.klean.shared.module.ModuleId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * M19: switching Calendar off through the real registry removes it from every surface: the nav entry, capture,
 * the Today tile, the status line, the Today widget rows, its event reminders and the "Klean" calendar in the
 * phone's calendar provider. Switching it back on restores them.
 */
@HiltAndroidTest
class CalendarDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var gate: ModuleGate
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var clock: Clock
    @Inject lateinit var zone: ZoneProvider
    @Inject lateinit var repo: CalendarRepository
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var contributions: DashboardContributions
    @Inject lateinit var statusLine: StatusLine
    @Inject lateinit var todayItems: TodayItems
    @Inject lateinit var mirror: MirrorHook
    @Inject lateinit var provider: AndroidCalendarProvider

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val ids = mutableListOf<String>()

    @Before fun setUp() = runBlocking {
        hilt.inject()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_CALENDAR)
        ui.grantRuntimePermission(ctx.packageName, Manifest.permission.WRITE_CALENDAR)
        gate.setEnabled(ModuleId.CALENDAR, true)
    }

    @After fun tearDown() = runBlocking {
        registry.setEnabled(ModuleId.CALENDAR, true)
        ids.forEach { repo.delete(it) }
        mirror.reschedule()
    }

    private suspend fun addEvents(): Pair<String, String> {
        val now = clock.now().toEpochMilliseconds()
        // Running now, so it is "next" today whatever the time of day.
        val running = repo.add(EventDraft("Test dentist", now - 60_000, now + 30 * 60_000, zone = zone.current().id)).also { ids += it }
        // Far enough ahead that its reminder is still to come.
        val later = repo.add(
            EventDraft("Test class", now + 2 * 86_400_000L, now + 2 * 86_400_000L + 3_600_000, zone = zone.current().id, reminderMinutes = 10),
        ).also { ids += it }
        return running to later
    }

    private suspend fun reminderLive(eventId: String): Boolean =
        db.reminderDao().get(EventReminderSync.reminderId(eventId))?.sync?.deleted == false

    @Test fun disablingCalendarRemovesItEverywhere() = runBlocking {
        val (_, later) = addEvents()
        mirror.reschedule()
        assertTrue("event reminder while on", reminderLive(later))
        assertTrue(capture.availableHandlers.first().any { it.moduleId == ModuleId.CALENDAR })
        assertTrue(contributions.summaries().first().any { it.key.startsWith("calendar.") })
        assertTrue(statusLine.fragments(max = 10).first().any { it.key.startsWith("calendar.") })
        assertTrue(todayItems.items().first().any { it.key.startsWith("calendar:") })
        assertNotNull("Klean calendar in the provider", provider.findKleanCalendar())

        registry.setEnabled(ModuleId.CALENDAR, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.CALENDAR })
        assertFalse(capture.availableHandlers.first().any { it.moduleId == ModuleId.CALENDAR })
        assertFalse(contributions.summaries().first().any { it.key.startsWith("calendar.") })
        assertFalse(statusLine.fragments(max = 10).first().any { it.key.startsWith("calendar.") })
        assertFalse(todayItems.items().first().any { it.key.startsWith("calendar:") })
        assertFalse("event reminder after off", reminderLive(later))
        assertNull("provider calendar after off", provider.findKleanCalendar())
        // A resync (boot, clock change) keeps it off.
        mirror.reschedule()
        assertNull(provider.findKleanCalendar())
    }

    @Test fun reEnablingBringsCalendarBack() = runBlocking {
        val (_, later) = addEvents()
        registry.setEnabled(ModuleId.CALENDAR, false)
        registry.setEnabled(ModuleId.CALENDAR, true)
        assertTrue(registry.enabled.first().any { it.id == ModuleId.CALENDAR })
        assertTrue(capture.availableHandlers.first().any { it.moduleId == ModuleId.CALENDAR })
        assertTrue("event reminder back", reminderLive(later))
        assertNotNull("provider calendar back", provider.findKleanCalendar())
    }
}
