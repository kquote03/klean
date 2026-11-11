package app.klean.feature.diet.export

import android.content.Context
import android.text.format.DateFormat
import app.klean.core.common.ZoneProvider
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.ui.DayCalories
import app.klean.feature.diet.ui.EntryKcal
import app.klean.feature.diet.ui.dayCalories
import app.klean.feature.diet.ui.entryKcal
import app.klean.shared.diet.FoodItemState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/** One food in the copied text. */
data class DayLine(val eatenAt: Long, val text: String, val kcal: EntryKcal?, val grams: Int? = null)

/** The words of the copied text (English defaults; the app passes its string resources). */
data class DayTextWords(
    val heading: (String) -> String = { "What I ate on $it" },
    val nothing: String = "(nothing logged)",
    val without: (Int) -> String = { n -> if (n == 1) "1 food without calories" else "$n foods without calories" },
    val total: (String) -> String = { "Total: $it" },
    val goal: (String) -> String = { "(goal $it)" },
)

/**
 * A day's food as plain text for pasting into any assistant:
 * ```
 * What I ate on Wednesday, 11 November 2026
 * 09:00  oatmeal: 150 kcal
 * 11:27  banana: ≈ 105 kcal
 * 13:10  salad
 * Total: ≈ 255 kcal (goal 2,000 kcal) · 1 food without calories
 * ```
 * With [showCalories] off there are no calories anywhere.
 */
fun dayText(
    entries: List<DayLine>,
    total: DayCalories,
    goal: Int,
    showCalories: Boolean,
    time: (Long) -> String,
    dayLabel: String,
    words: DayTextWords = DayTextWords(),
): String {
    val nf = NumberFormat.getIntegerInstance()
    fun kcal(n: Int, approx: Boolean) = (if (approx) "≈ " else "") + nf.format(n) + " kcal"
    val lines = mutableListOf(words.heading(dayLabel))
    if (entries.isEmpty()) {
        lines += words.nothing
        return lines.joinToString("\n")
    }
    entries.sortedBy { it.eatenAt }.forEach { e ->
        val k = e.kcal?.takeIf { showCalories }
        val amount = e.grams?.let { " (${nf.format(it)} g)" }.orEmpty()
        lines += "${time(e.eatenAt)}  ${e.text}$amount" + (k?.let { ": " + kcal(it.kcal, it.estimated) } ?: "")
    }
    if (showCalories) {
        val head = words.total(kcal(total.total, total.estimated)) + (if (goal > 0) " " + words.goal(kcal(goal, false)) else "")
        lines += listOfNotNull(head, total.missing.takeIf { it > 0 }?.let(words.without)).joinToString(" · ")
    }
    return lines.joinToString("\n")
}

/** Builds [dayText] for a logical day from the repository. */
fun interface DayTextProvider {
    suspend fun forDay(date: LocalDate): String
}

class AndroidDayTextProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: DietRepository,
    private val zone: ZoneProvider,
) : DayTextProvider {
    override suspend fun forDay(date: LocalDate): String {
        val entries = repo.observeDay(date).first()
        val items = repo.observeItems(entries.map { it.id }).first().filter { it.state != FoodItemState.REJECTED }
        val prefs = repo.observeViewPrefs().first()
        val byEntry = items.groupBy { it.entryId }
        val zoneId = ZoneId.of(zone.current().id)
        // Follows the phone's 12/24-hour setting.
        val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
        val timeFmt = DateTimeFormatter.ofPattern(pattern).withZone(zoneId)
        val dayLabel = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy").format(date.toJavaLocalDate())
        return dayText(
            entries = entries.map { DayLine(it.eatenAt, it.rawText, entryKcal(it, byEntry[it.id].orEmpty()), it.grams) },
            total = dayCalories(entries, items),
            goal = prefs.calorieGoal,
            showCalories = prefs.showCalories,
            time = { timeFmt.format(Instant.ofEpochMilli(it)) },
            dayLabel = dayLabel,
            words = DayTextWords(
                heading = { ctx.getString(R.string.diet_copy_heading, it) },
                nothing = ctx.getString(R.string.diet_copy_nothing),
                without = { ctx.resources.getQuantityString(R.plurals.diet_copy_without, it, it) },
                total = { ctx.getString(R.string.diet_copy_total, it) },
                goal = { ctx.getString(R.string.diet_copy_goal, it) },
            ),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DayTextModule {
    @Binds abstract fun provider(impl: AndroidDayTextProvider): DayTextProvider
}
