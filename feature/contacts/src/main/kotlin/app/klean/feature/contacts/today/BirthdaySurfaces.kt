package app.klean.feature.contacts.today

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.ContactEntity
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.DayItem
import app.klean.core.ui.module.DayItemKind
import app.klean.core.ui.module.DayItemsProvider
import app.klean.core.ui.module.StatusFragment
import app.klean.core.ui.module.StatusLineProvider
import app.klean.core.ui.module.StatusRefresh
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.core.ui.module.TodayItemsProvider
import app.klean.feature.contacts.ContactsKeys
import app.klean.feature.contacts.ContactsLinks
import app.klean.feature.contacts.ContactsRoute
import app.klean.feature.contacts.R
import app.klean.feature.contacts.birthday.Birthdays
import app.klean.feature.contacts.data.ContactChangeListener
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.contacts.data.birthday
import app.klean.shared.module.ModuleId
import app.klean.shared.time.atMinute
import app.klean.shared.time.toLocal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate

/** One coming birthday. [age] is the age turned, when the year is known and "Show age" is on. */
data class UpcomingBirthday(val contactId: String, val name: String, val date: LocalDate, val daysUntil: Int, val age: Int?)

/** Upcoming birthdays, pure. */
object BirthdayIndex {
    fun upcoming(contacts: List<ContactEntity>, today: LocalDate, withinDays: Int, showAge: Boolean): List<UpcomingBirthday> =
        contacts.filter { !it.sync.deleted }.mapNotNull { c ->
            val b = c.birthday ?: return@mapNotNull null
            val next = Birthdays.next(b, today)
            val days = Birthdays.daysUntil(b, today)
            if (days > withinDays) null else UpcomingBirthday(c.id, c.displayName, next, days, if (showAge) Birthdays.ageOn(b, next) else null)
        }.sortedWith(compareBy({ it.daysUntil }, { it.name.lowercase() }))

    fun between(contacts: List<ContactEntity>, from: LocalDate, to: LocalDate, showAge: Boolean): List<UpcomingBirthday> =
        contacts.filter { !it.sync.deleted }.flatMap { c ->
            val b = c.birthday ?: return@flatMap emptyList()
            Birthdays.between(b, from, to).map { d ->
                UpcomingBirthday(c.id, c.displayName, d, (d.toEpochDays() - from.toEpochDays()).toInt(), if (showAge) Birthdays.ageOn(b, d) else null)
            }
        }.sortedWith(compareBy({ it.date }, { it.name.lowercase() }))
}

/** The words the surfaces use. An interface so the surfaces are tested without resources. */
interface BirthdaySurfaceText {
    fun title(name: String, age: Int?): String
    fun statusToday(names: List<String>): String
    fun tileLabel(): String
    fun tileValue(name: String, date: LocalDate, daysUntil: Int): String
    fun tomorrow(): String
}

class AndroidBirthdaySurfaceText @Inject constructor(@ApplicationContext private val ctx: Context) : BirthdaySurfaceText {
    override fun title(name: String, age: Int?) =
        if (age != null && age > 0) ctx.getString(R.string.birthdays_item_age, name, age) else ctx.getString(R.string.birthdays_item, name)
    override fun statusToday(names: List<String>) =
        if (names.size == 1) ctx.getString(R.string.birthdays_status_one, names[0])
        else ctx.resources.getQuantityString(R.plurals.birthdays_status_many, names.size, names.size)
    override fun tileLabel() = ctx.getString(R.string.birthdays_tile_label)
    override fun tileValue(name: String, date: LocalDate, daysUntil: Int) = when (daysUntil) {
        0 -> ctx.getString(R.string.birthdays_tile_today, name)
        1 -> ctx.getString(R.string.birthdays_tile_tomorrow, name)
        else -> ctx.getString(R.string.birthdays_tile_value, name, shortDate(date))
    }
    override fun tomorrow() = ctx.getString(R.string.birthdays_tomorrow)

    companion object {
        /** "Mar 3". */
        fun shortDate(d: LocalDate): String =
            java.time.LocalDate.of(d.year, d.month.ordinal + 1, d.day).format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault()))
    }
}

/** Emits today's date now and whenever the day changes. */
open class BirthdayDays @Inject constructor(private val clock: Clock, private val zone: ZoneProvider) {
    open fun days(): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.now().toEpochMilliseconds().toLocal(zone.current()).date)
            delay(60_000)
        }
    }.distinctUntilChanged()
}

/**
 * Birthdays on the holistic surfaces: "Sara's birthday" on the status line on the day, today's and tomorrow's on the
 * Today widget, a "Next birthday · Sara, Mar 3" tile, birthdays per day in the week overview, and every birthday on the
 * calendar views ([DayItemsProvider], kind BIRTHDAY). Reads contacts one-shot (no live Room flow at startup) and
 * again after every contact write and at midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class BirthdaySurfaces @Inject constructor(
    private val repo: dagger.Lazy<ContactRepository>,
    private val settings: dagger.Lazy<SettingsRepository>,
    private val days: BirthdayDays,
    private val text: BirthdaySurfaceText,
    private val zone: ZoneProvider,
    private val statusRefresh: dagger.Lazy<StatusRefresh>,
) : StatusLineProvider, TodayItemsProvider, DashboardSummaryProvider, DayActivityProvider, DayItemsProvider, ContactChangeListener {
    override val moduleId: ModuleId = ModuleId.BIRTHDAYS
    private val tick = MutableStateFlow(0L)

    private class Snapshot(val today: LocalDate, val contacts: List<ContactEntity>, val showAge: Boolean)

    private val snapshot: Flow<Snapshot> = combine(tick, days.days()) { _, d -> d }.mapLatest { d ->
        Snapshot(d, repo.get().live().filter { it.birthdayMonth != null }, settings.get().get(ContactsKeys.ShowAge))
    }

    override suspend fun onContactsChanged(ids: List<String>) {
        tick.value++
        statusRefresh.get().refresh()
    }

    override fun fragments(): Flow<List<StatusFragment>> = snapshot.map { s ->
        val today = BirthdayIndex.upcoming(s.contacts, s.today, 0, s.showAge)
        if (today.isEmpty()) emptyList() else listOf(StatusFragment(KEY_STATUS, text.statusToday(today.map { it.name }), PRIORITY))
    }

    override fun items(): Flow<List<TodayItem>> = snapshot.map { s ->
        BirthdayIndex.upcoming(s.contacts, s.today, 1, s.showAge).take(5).map {
            TodayItem(
                key = "birthday:${it.contactId}",
                timeLabel = if (it.daysUntil == 1) text.tomorrow() else null,
                text = text.title(it.name, it.age),
                sortKey = atMinute(it.date, 0, zone.current()),
                route = ContactsLinks.contact(it.contactId),
            )
        }
    }

    override fun summaries(): Flow<List<SummaryItem>> = snapshot.map { s ->
        val next = BirthdayIndex.upcoming(s.contacts, s.today, 366, s.showAge).firstOrNull() ?: return@map emptyList()
        listOf(SummaryItem(KEY_TILE, text.tileLabel(), text.tileValue(next.name, next.date, next.daysUntil), Icons.Rounded.Cake, ContactsRoute(ContactsLinks.TAB_BIRTHDAYS)))
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> = snapshot.map { s ->
        BirthdayIndex.between(s.contacts, from, to, false).groupingBy { it.date }.eachCount()
    }

    override fun dayRoute(date: LocalDate): Any = ContactsRoute(ContactsLinks.TAB_BIRTHDAYS)

    override fun items(from: LocalDate, to: LocalDate): Flow<List<DayItem>> = snapshot.map { s ->
        BirthdayIndex.between(s.contacts, from, to, s.showAge).map {
            DayItem(
                key = "birthday:${it.contactId}:${it.date}", date = it.date, title = text.title(it.name, it.age),
                kind = DayItemKind.BIRTHDAY, route = ContactsLinks.contact(it.contactId),
            )
        }
    }

    /** Coming birthdays for the Today card: within [withinDays], soonest first. */
    fun upcoming(withinDays: Int): Flow<Pair<LocalDate, List<UpcomingBirthday>>> = snapshot.map { s ->
        s.today to BirthdayIndex.upcoming(s.contacts, s.today, withinDays, s.showAge)
    }

    companion object {
        const val KEY_STATUS = "birthdays.today"
        const val KEY_TILE = "birthdays.next"
        const val PRIORITY = 25
    }
}
