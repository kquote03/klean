package app.klean.feature.diet.ui

import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.shared.diet.KcalSource

/** One entry's calories: the user's own ([estimated] false), or filled in from earlier entries or by AI. */
data class EntryKcal(val kcal: Int, val estimated: Boolean, val source: KcalSource)

/**
 * The entry's calories: `food_entry.kcal` when set, else (older entries) the sum of its visible AI items.
 * Null when there are none.
 */
fun entryKcal(e: FoodEntryEntity, items: List<FoodItemEntity>): EntryKcal? {
    e.kcal?.let { k ->
        val source = KcalSource.entries.firstOrNull { it.name == e.kcalSource } ?: KcalSource.AI
        return EntryKcal(k, source != KcalSource.USER, source)
    }
    return knownCalories(items.filter { it.entryId == e.id })?.let { EntryKcal(it, true, KcalSource.AI) }
}

/** A day's calories: entries without any count as zero and are counted in [missing]. */
data class DayCalories(val total: Int, val estimated: Boolean, val missing: Int)

fun dayCalories(entries: List<FoodEntryEntity>, items: List<FoodItemEntity>): DayCalories {
    val byEntry = items.groupBy { it.entryId }
    val known = entries.filterNot { it.sync.deleted }.map { entryKcal(it, byEntry[it.id].orEmpty()) }
    return DayCalories(
        total = known.sumOf { it?.kcal ?: 0 },
        estimated = known.any { it?.estimated == true },
        missing = known.count { it == null },
    )
}

/** "300 kcal" for the user's own number, "≈ 105 kcal" for a filled-in one. */
fun DietFormatter.kcalLabel(k: EntryKcal): String = if (k.estimated) kcalApprox(k.kcal) else kcalExact(k.kcal)

/** The entry screen's line: "300 kcal", "≈ 105 kcal · from earlier", "≈ 260 kcal · estimate". */
fun DietFormatter.kcalWithSource(k: EntryKcal): String = when (k.source) {
    KcalSource.USER -> kcalExact(k.kcal)
    KcalSource.HISTORY -> kcalFromEarlier(kcalApprox(k.kcal))
    KcalSource.AI -> kcalEstimate(kcalApprox(k.kcal))
}

/** "≈ 1,240 kcal so far" (or without ≈ when all are the user's own); null when calories are off or none are known. */
fun DietFormatter.daySoFar(day: DayCalories, showCalories: Boolean): String? {
    if (!showCalories || day.total <= 0) return null
    return kcalSoFar(if (day.estimated) kcalApprox(day.total) else kcalExact(day.total))
}
