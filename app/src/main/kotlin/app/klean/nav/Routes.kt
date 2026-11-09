package app.klean.nav

import kotlinx.serialization.Serializable

@Serializable object TodayRoute

@Serializable object SettingsRoute

/** Reorder and hide Today's cards. */
@Serializable object ArrangeTodayRoute

/** Modules that don't fit in the bottom bar, plus Settings. */
@Serializable object MoreRoute
