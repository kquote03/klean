package app.klean.feature.diet

import kotlinx.serialization.Serializable

@Serializable object DietRoute
@Serializable data class FoodEntryRoute(val entryId: String)
@Serializable object DietHistoryRoute
@Serializable data class DietDayRoute(val isoDate: String)
@Serializable object DietSettingsRoute
