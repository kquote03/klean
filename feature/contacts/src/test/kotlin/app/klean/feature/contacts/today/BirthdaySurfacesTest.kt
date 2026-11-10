package app.klean.feature.contacts.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.core.ui.module.DayItemKind
import app.klean.core.ui.module.StatusRefresh
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.ContactsRoute
import app.klean.feature.contacts.DubaiZone
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.contact
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.date
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.tempDevicePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

object FakeSurfaceText : BirthdaySurfaceText {
    override fun title(name: String, age: Int?) = if (age != null) "$name turns $age" else "$name's birthday"
    override fun statusToday(names: List<String>) = if (names.size == 1) "${names[0]}'s birthday" else "${names.size} birthdays today"
    override fun tileLabel() = "Next birthday"
    override fun tileValue(name: String, date: LocalDate, daysUntil: Int) = "$name, $date"
    override fun tomorrow() = "Tomorrow"
}

class BirthdayIndexTest {
    @Test fun upcomingIsSoonestFirstWithinTheWindow() {
        val today = date(2026, 11, 10)
        val l = BirthdayIndex.upcoming(
            listOf(contact("a", "Sara", 11, 12), contact("b", "Omar", 11, 10, 1990), contact("c", "Lina", 12, 31), contact("d", "Gone", 11, 11, deleted = true)),
            today, 30, showAge = true,
        )
        assertEquals(listOf("Omar", "Sara"), l.map { it.name })
        assertEquals(36, l.first().age)
        assertEquals(0, l.first().daysUntil)
    }
}

@RunWith(AndroidJUnit4::class)
class BirthdaySurfacesTest {
    private val today = date(2026, 11, 10)
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val settings = SettingsRepository(db, stamper)
    private val repo = DefaultContactRepository(db, stamper, clock, settings, emptySet())
    private var refreshed = 0
    private val days = object : BirthdayDays(clock, DubaiZone) { override fun days() = flowOf(today) }
    private val surfaces = BirthdaySurfaces(
        { repo }, { settings }, days, FakeSurfaceText, DubaiZone, { StatusRefresh { emptySet() } },
    )

    @After fun close() = db.close()

    @Test fun statusLineOnlyOnTheDay() = runBlocking {
        repo.add(ContactDraft("Sara", birthday = Birthday(11, 12)))
        assertTrue(surfaces.fragments().first().isEmpty())
        repo.add(ContactDraft("Omar", birthday = Birthday(11, 10)))
        surfaces.onContactsChanged(emptyList())
        assertEquals(listOf("Omar's birthday"), surfaces.fragments().first().map { it.text })
    }

    @Test fun tileTodayItemsActivityAndCalendarItems() = runBlocking {
        val sara = repo.add(ContactDraft("Sara", birthday = Birthday(11, 11, 2000)))
        repo.add(ContactDraft("Lina", birthday = Birthday(2, 29)))
        val tile = surfaces.summaries().first().single()
        assertEquals("Sara, 2026-11-11", tile.value)
        assertEquals(ContactsRoute("birthdays"), tile.route)
        val items = surfaces.items().first()
        assertEquals(listOf("Sara turns 26"), items.map { it.text })
        assertEquals("Tomorrow", items.single().timeLabel)
        assertEquals("klean://contacts/contact/$sara", items.single().route)
        assertEquals(mapOf(date(2026, 11, 11) to 1), surfaces.activity(date(2026, 11, 9), date(2026, 11, 15)).first())
        val cal = surfaces.items(date(2027, 2, 1), date(2027, 2, 28)).first()
        assertEquals(listOf(date(2027, 2, 28)), cal.map { it.date })
        assertEquals(DayItemKind.BIRTHDAY, cal.single().kind)
        settings.set(ContactsKeys.ShowAge, false)
        assertEquals(listOf("Sara's birthday"), surfaces.items().first().map { it.text })
    }

    @Test fun nothingWithoutBirthdays() = runBlocking {
        repo.add(ContactDraft("Sara"))
        assertTrue(surfaces.summaries().first().isEmpty())
        assertTrue(surfaces.items().first().isEmpty())
    }
}
