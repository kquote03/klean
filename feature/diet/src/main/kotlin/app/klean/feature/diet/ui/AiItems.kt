package app.klean.feature.diet.ui

import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.feature.diet.R
import app.klean.shared.ai.ProviderId
import app.klean.shared.diet.FoodItemState

/** "shawarma · 1 wrap", plus "· ~450 kcal" only when the user turned calories on. */
fun itemLine(item: FoodItemEntity, showCalories: Boolean, fmt: DietFormatter): String = buildString {
    append(item.name)
    item.portion?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    if (showCalories) item.calories?.let { append(" · ").append(fmt.kcal(it)) }
}

/** "~1,850 kcal so far" for the day's visible items, or null when calories are off or unknown. */
fun caloriesSoFar(items: List<FoodItemEntity>, showCalories: Boolean, fmt: DietFormatter): String? {
    if (!showCalories) return null
    val known = items.filter { !it.sync.deleted && it.state != FoodItemState.REJECTED }.mapNotNull { it.calories }
    if (known.isEmpty()) return null
    return fmt.kcalSoFar(known.sum())
}

/** Which provider produced an item, as a chip label. Null for items the user wrote. */
fun providerLabelRes(wire: String?): Int? = when (wire) {
    ProviderId.GEMINI.wire -> R.string.diet_provider_gemini
    ProviderId.OPENAI_COMPAT.wire -> R.string.diet_provider_local
    ProviderId.ON_DEVICE.wire -> R.string.diet_provider_on_device
    else -> null
}

/** The provider shown for an entry's visible items (the first one that isn't the user). */
fun providerOf(items: List<FoodItemEntity>): String? =
    items.firstOrNull { it.state != FoodItemState.REJECTED && providerLabelRes(it.providerId) != null }?.providerId
