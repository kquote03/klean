package app.klean.feature.diet.ui.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.LocalFireDepartment
import android.content.Context
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.ui.module.DashboardSummaryProvider
import app.klean.core.ui.module.DayActivityProvider
import app.klean.core.ui.module.SummaryItem
import app.klean.feature.diet.DietDayRoute
import app.klean.feature.diet.DietRoute
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.data.DietViewPrefs
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.knownCalories
import app.klean.feature.diet.ui.observeCountsByLogicalDay
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Copy for diet's Today tiles, behind an interface so the providers are tested without resources. */
interface DietSummaryText {
    fun todayLabel(): String
    /** "No entries yet" / "1 entry" / "3 entries". */
    fun count(n: Int): String
    fun lastLabel(): String
    /** "shawarma · 1:15 PM". */
    fun last(text: String, time: String): String
    fun nextLabel(): String
    /** "around 5:00 PM". */
    fun next(time: String): String
    fun kcalLabel(): String
}

class AndroidDietSummaryText @Inject constructor(@ApplicationContext private val ctx: Context) : DietSummaryText {
    override fun todayLabel() = ctx.getString(R.string.diet_summary_today)
    override fun count(n: Int) =
        if (n == 0) ctx.getString(R.string.diet_summary_none) else ctx.resources.getQuantityString(R.plurals.diet_summary_count, n, n)
    override fun lastLabel() = ctx.getString(R.string.diet_summary_last)
    override fun last(text: String, time: String) = ctx.getString(R.string.diet_summary_last_value, text, time)
    override fun nextLabel() = ctx.getString(R.string.diet_summary_next)
    override fun next(time: String) = ctx.getString(R.string.diet_summary_next_value, time)
    override fun kcalLabel() = ctx.getString(R.string.diet_summary_kcal)
}

/**
 * Diet's contributions to Today: tiles (today's count, the last entry, the next check-in when
 * check-ins are on, calories so far when Show calories is on) and per-day entry counts for the
 * week strip. Built through a @Provides in [app.klean.feature.diet.DietDashboardBindings] so the
 * settings sources are plain lambdas here (tests need no DataStore or Room).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietDashboardProviders(
    private val repo: DietRepository,
    private val fmt: DietFormatter,
    private val text: DietSummaryText,
    private val ticker: MinuteTicker,
    private val zone: ZoneProvider,
    private val nextCheckIn: () -> Flow<Long?>,
    private val promptsOn: () -> Flow<Boolean>,
) : DashboardSummaryProvider, DayActivityProvider {
    override val moduleId: ModuleId = ModuleId.DIET

    private data class Day(val entries: List<FoodEntryEntity>, val items: List<FoodItemEntity>)

    /** Today's entries and their items, following the day as it rolls over while Today is open. */
    private fun today(): Flow<Day> = ticker.ticks()
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date -> repo.observeDay(date) }
        .flatMapLatest { entries -> repo.observeItems(entries.map { it.id }).map { Day(entries, it) } }

    override fun summaries(): Flow<List<SummaryItem>> =
        combine(today(), repo.observeViewPrefs(), promptsOn(), nextCheckIn()) { day, prefs, on, next ->
            tiles(day, prefs, on, next)
        }

    private fun tiles(day: Day, prefs: DietViewPrefs, promptsOn: Boolean, next: Long?): List<SummaryItem> = buildList {
        val entries = day.entries
        add(SummaryItem(KEY_TODAY, text.todayLabel(), text.count(entries.size), icon = Icons.Rounded.Restaurant, route = DietRoute))
        entries.maxByOrNull { it.eatenAt }?.let { last ->
            add(SummaryItem(KEY_LAST, text.lastLabel(), text.last(shorten(last.rawText), fmt.time(last.eatenAt)), icon = Icons.Rounded.History, route = DietRoute))
        }
        if (promptsOn && next != null) {
            add(SummaryItem(KEY_NEXT, text.nextLabel(), text.next(fmt.time(next)), icon = Icons.Rounded.Schedule, route = DietRoute))
        }
        if (prefs.showCalories) {
            knownCalories(day.items)?.let { add(SummaryItem(KEY_KCAL, text.kcalLabel(), fmt.kcal(it), icon = Icons.Rounded.LocalFireDepartment, route = DietRoute)) }
        }
    }

    override fun activity(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        repo.observeCountsByLogicalDay(from, to) { zone.current() }

    override fun dayRoute(date: LocalDate): Any = if (date == repo.todayDate()) DietRoute else DietDayRoute(date.toString())

    companion object {
        const val KEY_TODAY = "diet.today"
        const val KEY_LAST = "diet.last"
        const val KEY_NEXT = "diet.next"
        const val KEY_KCAL = "diet.kcal"
        private const val MAX_TEXT = 40

        internal fun shorten(s: String): String = if (s.length <= MAX_TEXT) s else s.take(MAX_TEXT - 1) + "…"
    }
}
