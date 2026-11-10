package app.klean.feature.calendar

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Every calendar screen lives in this nested graph, so the bottom bar can tell they belong to Calendar. */
@Serializable object CalendarGraph
/** The calendar, on the agenda for today. */
@Serializable object CalendarRoute
/** The calendar's day view for [isoDate] (Today's week strip, the Today widget). */
@Serializable data class CalendarDayRoute(val isoDate: String)
/** [eventId] null adds an event (on [isoDate] when given); [occurrenceStart] is the tapped occurrence, or -1. */
@Serializable data class EventEditRoute(val eventId: String? = null, val isoDate: String? = null, val occurrenceStart: Long = -1L)
@Serializable object CalendarSettingsRoute

object CalendarRoutes {
    const val DEEP_LINK = "klean://calendar"
    const val DEEP_LINK_DAY = "klean://calendar/day/{isoDate}"
    fun dayLink(date: LocalDate) = "klean://calendar/day/$date"
}
