package app.klean.feature.diet.notification

import androidx.annotation.StringRes
import app.klean.feature.diet.R
import app.klean.shared.diet.prompt.MealLabel

/** A check-in wording. [id] is stored in `prompt_log.phrasing_id`, so ids never change once shipped. */
data class Phrasing(val id: String, @StringRes val res: Int)

/** Rotating wordings, so an ignored check-in comes back looking different rather than more often. */
object PromptPhrasings {
    val generic = listOf(
        Phrasing("g1", R.string.prompt_g1), Phrasing("g2", R.string.prompt_g2),
        Phrasing("g3", R.string.prompt_g3), Phrasing("g4", R.string.prompt_g4),
        Phrasing("g5", R.string.prompt_g5), Phrasing("g6", R.string.prompt_g6),
        Phrasing("g7", R.string.prompt_g7), Phrasing("g8", R.string.prompt_g8),
    )

    fun sinceMeal(label: MealLabel): List<Phrasing> = when (label) {
        MealLabel.BREAKFAST -> listOf(Phrasing("m_b1", R.string.prompt_since_breakfast), Phrasing("m_b2", R.string.prompt_since_breakfast_2))
        MealLabel.LUNCH -> listOf(Phrasing("m_l1", R.string.prompt_since_lunch), Phrasing("m_l2", R.string.prompt_since_lunch_2))
        MealLabel.DINNER -> listOf(Phrasing("m_d1", R.string.prompt_since_dinner), Phrasing("m_d2", R.string.prompt_since_dinner_2))
        MealLabel.LATE -> listOf(Phrasing("m_n1", R.string.prompt_late), Phrasing("m_n2", R.string.prompt_late_2))
    }

    /** "What did you order?", after a delivery app says the order arrived (M12). Not rotated. */
    const val DELIVERY_ID = "delivery"
    val delivery = Phrasing(DELIVERY_ID, R.string.prompt_delivery)

    /** "Anything from %1$s?", as the user leaves a place (M15). Formatted with the place's name. */
    const val GEOFENCE_ID = "geofence"
    val geofence = Phrasing(GEOFENCE_ID, R.string.prompt_geofence)

    fun byId(id: String): Phrasing? = (generic + MealLabel.entries.flatMap(::sinceMeal) + delivery + geofence).firstOrNull { it.id == id }
}
