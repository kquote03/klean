package app.klean.feature.diet.ui

import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.photo.PhotoStorage
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.diet.data.DietViewPrefs
import app.klean.feature.diet.ui.today.DietRow
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.GapDetector
import app.klean.shared.time.MINUTE_MS
import kotlinx.datetime.LocalDate

/**
 * Turns one logical day of entries into rows with neutral gap markers. Shared by Today and the day
 * pager, so both lists look the same. For a past day pass the end of that day as [build]'s `now`.
 */
class DietRowBuilder(
    private val repo: DietRepository,
    private val fmt: DietFormatter,
    private val photos: PhotoStorage,
) {
    fun build(
        date: LocalDate,
        entries: List<FoodEntryEntity>,
        items: List<FoodItemEntity>,
        prefs: DietViewPrefs,
        now: Long,
    ): List<DietRow> {
        val window = repo.windowFor(date)
        val gaps = GapDetector.find(entries.map { it.eatenAt }, window, now, prefs.gapThresholdMinutes * MINUTE_MS)
        val byEntry = items.filter { it.state != FoodItemState.REJECTED }.groupBy { it.entryId }
        val entryRows = entries.map { e ->
            val its = byEntry[e.id].orEmpty()
            val summary = its.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }
                ?.let { s ->
                    val kcal = its.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum()
                    if (prefs.showCalories && kcal != null) "$s · $kcal kcal" else s
                }
            e.eatenAt to DietRow.Entry(
                id = e.id,
                timeLabel = fmt.time(e.eatenAt),
                text = e.rawText,
                itemsSummary = summary,
                providerName = its.firstOrNull { it.providerId != "user" }?.providerId,
                photoPath = e.photoFile?.let { photos.file(it).path },
            )
        }
        val gapRows = gaps.map { g -> g.start to DietRow.GapRow(fmt.gap(g), g.start, g.midpoint) }
        // At equal times the entry comes first: a gap starts after the entry that opens it.
        return (entryRows.map { Triple(it.first, 0, it.second) } + gapRows.map { Triple(it.first, 1, it.second) })
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
    }
}
