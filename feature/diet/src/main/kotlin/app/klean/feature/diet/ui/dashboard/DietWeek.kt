package app.klean.feature.diet.ui.dashboard

import android.content.Context
import app.klean.core.ui.module.WeekSummary
import app.klean.core.ui.module.WeekSummaryProvider
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DietFormatter
import app.klean.shared.module.ModuleId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Diet on Track › Week, as in the prototype: "Food today · 1 entry · Oatmeal, banana at 8:10". Nothing on a day
 * without entries. The repository is lazy: the Week's providers are built with the app shell.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietWeekSummary @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: dagger.Lazy<DietRepository>,
    private val fmt: DietFormatter,
) : WeekSummaryProvider {
    override val moduleId: ModuleId = ModuleId.DIET

    override fun week(days: List<LocalDate>): Flow<WeekSummary?> =
        flow { emit(repo.get().todayDate()) }.flatMapLatest { repo.get().observeDay(it) }.map { entries ->
            val latest = entries.maxByOrNull { it.eatenAt } ?: return@map null
            WeekSummary(
                moduleId,
                ctx.getString(R.string.diet_week_label),
                ctx.resources.getQuantityString(R.plurals.diet_summary_count, entries.size, entries.size),
                ctx.getString(R.string.diet_week_latest, latest.rawText, fmt.time(latest.eatenAt)),
            )
        }
}
