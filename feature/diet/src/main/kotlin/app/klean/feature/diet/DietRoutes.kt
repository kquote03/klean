package app.klean.feature.diet

import kotlinx.serialization.Serializable

/** Every diet screen lives in this nested graph, so the bottom bar can tell they belong to Diet. */
@Serializable object DietGraph
@Serializable object DietRoute
@Serializable data class FoodEntryRoute(val entryId: String)
@Serializable object DietHistoryRoute
@Serializable data class DietDayRoute(val isoDate: String)
@Serializable object DietSettingsRoute
@Serializable object DeliveryAppsRoute
