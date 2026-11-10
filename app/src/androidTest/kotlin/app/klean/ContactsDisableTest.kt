package app.klean

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.klean.core.capture.CaptureResult
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.DashboardSlot
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.TodayItems
import app.klean.feature.contacts.birthday.BirthdayReminderSync
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.mirror.AndroidContactsProvider
import app.klean.feature.contacts.mirror.ContactsMirrorHook
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import app.klean.shared.time.toLocal
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * M19: switching Contacts off removes its nav entry, capture handler and the phone's copy under the Klean account (a
 * birthday phrase then becomes a dated task); switching Birthdays off removes its Today card, tile, status fragment,
 * Today-widget rows and every birthday reminder. Switching them back on restores everything.
 */
@HiltAndroidTest
class ContactsDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var gate: ModuleGate
    @Inject lateinit var db: KleanDatabase
    @Inject lateinit var clock: Clock
    @Inject lateinit var zone: ZoneProvider
    @Inject lateinit var repo: ContactRepository
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var contributions: DashboardContributions
    @Inject lateinit var statusLine: StatusLine
    @Inject lateinit var todayItems: TodayItems
    @Inject lateinit var mirror: ContactsMirrorHook
    @Inject lateinit var provider: AndroidContactsProvider

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val ids = mutableListOf<String>()

    @Before fun setUp() = runBlocking {
        hilt.inject()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(ctx.packageName, Manifest.permission.READ_CONTACTS)
        ui.grantRuntimePermission(ctx.packageName, Manifest.permission.WRITE_CONTACTS)
        listOf(ModuleId.CONTACTS, ModuleId.BIRTHDAYS, ModuleId.TASKS).forEach { gate.setEnabled(it, true) }
    }

    @After fun tearDown() = runBlocking {
        registry.setEnabled(ModuleId.CONTACTS, true)
        registry.setEnabled(ModuleId.BIRTHDAYS, true)
        ids.forEach { repo.delete(it) }
        mirror.reschedule()
    }

    /** A contact whose birthday is today, so every Birthdays surface has something to show. */
    private suspend fun addToday(): String {
        val today = clock.now().toEpochMilliseconds().toLocal(zone.current()).date
        return repo.add(ContactDraft("Test Sara", birthday = Birthday(today.month.ordinal + 1, today.day), reminderDays = listOf(0, 3))).also { ids += it }
    }

    private suspend fun reminderLive(contactId: String, n: Int): Boolean =
        db.reminderDao().get(BirthdayReminderSync.reminderId(contactId, n))?.sync?.deleted == false

    @Test fun disablingContactsRemovesItsEntryCaptureAndProviderRows() = runBlocking {
        val id = addToday()
        mirror.reschedule()
        assertTrue("mirrored while on", id in provider.sourceIds())
        assertTrue(registry.enabled.first().any { it.id == ModuleId.CONTACTS })
        assertTrue(capture.availableHandlers.first().any { it.moduleId == ModuleId.CONTACTS })

        registry.setEnabled(ModuleId.CONTACTS, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.CONTACTS })
        assertFalse(capture.availableHandlers.first().any { it.moduleId == ModuleId.CONTACTS })
        assertFalse("provider rows after off", id in provider.sourceIds())
        mirror.reschedule()
        assertFalse("a resync keeps them off", id in provider.sourceIds())
        // A birthday phrase becomes a dated task, never AI.
        val saved = capture.submit(CaptureInput("Test Omar's birthday is March 3", CaptureSource.DASHBOARD, clock.now().toEpochMilliseconds()))
        assertEquals(ModuleId.TASKS, (saved as CaptureResult.Saved).moduleId)
        capture.undo(saved)
        // Birthdays stay on their own.
        assertTrue(reminderLive(id, 0))
    }

    @Test fun disablingBirthdaysRemovesItsSurfacesAndReminders() = runBlocking {
        val id = addToday()
        assertTrue(reminderLive(id, 0))
        assertTrue(registry.dashboardCards.first().any { it.dashboardSlot == DashboardSlot.BIRTHDAYS })
        assertTrue(contributions.summaries().first().any { it.key.startsWith("birthdays.") })
        assertTrue(statusLine.fragments(max = 10).first().any { it.key.startsWith("birthdays.") })
        assertTrue(todayItems.items().first().any { it.key.startsWith("birthday:") })

        registry.setEnabled(ModuleId.BIRTHDAYS, false)

        assertFalse(registry.dashboardCards.first().any { it.dashboardSlot == DashboardSlot.BIRTHDAYS })
        assertFalse(contributions.summaries().first().any { it.key.startsWith("birthdays.") })
        assertFalse(statusLine.fragments(max = 10).first().any { it.key.startsWith("birthdays.") })
        assertFalse(todayItems.items().first().any { it.key.startsWith("birthday:") })
        assertFalse("reminder on the day after off", reminderLive(id, 0))
        assertFalse("reminder 3 days before after off", reminderLive(id, 3))
    }

    @Test fun reEnablingBringsBothBack() = runBlocking {
        val id = addToday()
        registry.setEnabled(ModuleId.CONTACTS, false)
        registry.setEnabled(ModuleId.BIRTHDAYS, false)
        registry.setEnabled(ModuleId.CONTACTS, true)
        registry.setEnabled(ModuleId.BIRTHDAYS, true)
        assertTrue(capture.availableHandlers.first().any { it.moduleId == ModuleId.CONTACTS })
        assertTrue("provider rows back", id in provider.sourceIds())
        assertTrue("reminder back", reminderLive(id, 0))
        assertTrue(registry.dashboardCards.first().any { it.dashboardSlot == DashboardSlot.BIRTHDAYS })
    }
}
