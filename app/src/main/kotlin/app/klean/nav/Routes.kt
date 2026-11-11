package app.klean.nav

import kotlinx.serialization.Serializable

/** The top-level pages (Today, then each group's tabs) in one pager: the root of every stack. */
@Serializable object PagesRoute

/** Today. Reaching it as a destination (a link, `klean://today`) shows Today in the pager instead. */
@Serializable object TodayRoute

@Serializable object SettingsRoute

/** Reorder and hide Today's cards. */
@Serializable object ArrangeTodayRoute

/** Track › Week: a neutral summary of the trackers, Track's default until the user picks a module. */
@Serializable object TrackWeekRoute

/** The account menu (Today's avatar): the Google account, switching accounts, then Settings and Help. */
@Serializable object AccountRoute
