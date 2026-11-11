package app.klean.feature.contacts.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.contacts.ContactEditRoute
import app.klean.feature.contacts.DubaiZone
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.date
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.tempDevicePrefs
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeopleGlanceProviderTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val today = date(2026, 11, 10)
    private val clock = FakeClock(dubai(2026, 11, 10, 0, 30))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = DefaultContactRepository(db, stamper, clock, settings, emptySet())
    private val days = object : BirthdayDays(clock, DubaiZone) { override fun days() = flowOf(today) }
    private val surfaces = BirthdaySurfaces({ repo }, { settings }, days, FakeSurfaceText, DubaiZone, { StatusRefresh { emptySet() } })
    private val birthdays = BirthdayGlanceProvider(ctx, surfaces, FakeSurfaceText, DubaiZone)

    @After fun close() = db.close()

    @Test fun birthdaysTodayAndSoonNeedAttention() = runBlocking {
        repo.add(ContactDraft("Sam", birthday = Birthday(11, 10)))
        repo.add(ContactDraft("Sara", birthday = Birthday(11, 12)))
        repo.add(ContactDraft("Yesterday", birthday = Birthday(11, 9)))
        repo.add(ContactDraft("Later", birthday = Birthday(11, 20)))
        val g = birthdays.glance().first()
        assertEquals(listOf("Sam's birthday", "Sara's birthday"), g.attention.map { it.text })
        assertEquals(listOf("Today", "Thursday"), g.attention.map { it.detail })
        assertTrue(g.attention.all { it.module == ModuleId.BIRTHDAYS && it.route!!.startsWith("klean://contacts/contact/") })
        assertTrue(g.upcoming.isEmpty() && g.done.isEmpty())
        assertFalse(g.attention.any { "!" in it.text })
    }

    @Test fun contactIsOneTapToTheEditor() = runBlocking {
        val a = PeopleGlanceProvider(ctx).glance().first().quickAction
        assertEquals(QuickAction.Open(ModuleId.CONTACTS, "Contact", a!!.icon, ContactEditRoute()), a)
    }
}
