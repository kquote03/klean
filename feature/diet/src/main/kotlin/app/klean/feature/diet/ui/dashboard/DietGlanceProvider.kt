package app.klean.feature.diet.ui.dashboard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.core.ui.module.TodayGlanceProvider
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.notification.DietStatusActions
import app.klean.feature.diet.ui.DietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.entryKcal
import app.klean.feature.diet.ui.kcalLabel
import app.klean.shared.diet.FoodItemState
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Diet on Today's whiteboard: every food eaten today by name and time (never a count), the next check-in when
 * check-ins are on, and the one-tap "Food" button (the capture sheet aimed at Diet, with its "Log again" chips).
 * Built in [DietGlanceBindings], so the settings sources are plain lambdas here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietGlanceProvider(
    private val ctx: Context,
    private val repo: DietRepository,
    private val fmt: DietFormatter,
    private val ticker: MinuteTicker,
    private val nextCheckIn: () -> Flow<Long?>,
    private val promptsOn: () -> Flow<Boolean>,
) : TodayGlanceProvider {
    override val moduleId: ModuleId = ModuleId.DIET

    private data class Day(val entries: List<FoodEntryEntity>, val items: List<FoodItemEntity>)

    /** The logical day's entries and their items, following the day as it rolls over. */
    private fun today(): Flow<Day> = ticker.ticks()
        .map { repo.todayDate() }
        .distinctUntilChanged()
        .flatMapLatest { date -> repo.observeDay(date) }
        .flatMapLatest { entries -> repo.observeItems(entries.map { it.id }).map { Day(entries, it) } }

    override fun glance(): Flow<GlanceContribution> =
        combine(today(), repo.observeViewPrefs(), promptsOn(), nextCheckIn()) { day, prefs, on, next ->
            val byEntry = day.items.filter { !it.sync.deleted && it.state != FoodItemState.REJECTED }.groupBy { it.entryId }
            GlanceContribution(
                upcoming = if (on && next != null) {
                    listOf(GlanceRow(KEY_NEXT, moduleId, ctx.getString(R.string.diet_glance_checkin), next, icon = Icons.Rounded.Schedule, route = LINK))
                } else {
                    emptyList()
                },
                done = day.entries.map { e ->
                    val items = byEntry[e.id].orEmpty().sortedBy { it.position }
                    GlanceRow(
                        key = "diet:${e.id}",
                        module = moduleId,
                        text = e.rawText,
                        at = e.eatenAt,
                        detail = detail(e, items, prefs.showCalories),
                        icon = Icons.Rounded.Restaurant,
                        route = LINK,
                    )
                },
                quickAction = QuickAction.Capture(moduleId, ctx.getString(R.string.diet_glance_food), Icons.Rounded.Restaurant),
            )
        }

    /** "chicken, garlic sauce", plus "300 kcal" / "≈ 450 kcal" when Show calories is on; nothing that repeats the entry's text. */
    private fun detail(e: FoodEntryEntity, items: List<FoodItemEntity>, showCalories: Boolean): String? {
        val names = items.map { it.name }.takeUnless { it.size == 1 && it[0].equals(e.rawText.trim(), ignoreCase = true) }
            ?.joinToString(", ")?.takeIf { it.isNotBlank() }
        val kcal = if (showCalories) entryKcal(e, items)?.let(fmt::kcalLabel) else null
        return listOfNotNull(names, kcal).joinToString(" · ").ifEmpty { null }
    }

    companion object {
        const val KEY_NEXT = "diet:next-check-in"
        /** Today's rows are today's entries, so they open Diet's today screen. */
        val LINK: String = DietStatusActions.DEEP_LINK_DIET
    }
}

/** Puts Diet on Today's whiteboard, with the same check-in sources as its tiles. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object DietGlanceBindings {
    @dagger.Provides @dagger.multibindings.IntoSet
    fun glance(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: Context,
        repo: DietRepository,
        fmt: DietFormatter,
        ticker: MinuteTicker,
        prefs: app.klean.core.data.prefs.DevicePrefs,
        settings: app.klean.core.data.settings.SettingsRepository,
    ): TodayGlanceProvider = DietGlanceProvider(
        ctx, repo, fmt, ticker,
        nextCheckIn = { prefs.nextPromptAt },
        promptsOn = { settings.observe(app.klean.core.data.settings.SyncedKeys.DietPromptsEnabled) },
    )
}
