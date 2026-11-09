package app.klean.nav

/** Bottom bar: Today + up to [max] modules + (More | Settings). Keeps the bar at 5 items or fewer. */
data class NavLayout<T>(val primary: List<T>, val overflow: List<T>)

fun <T> navLayout(enabled: List<T>, max: Int = 3): NavLayout<T> =
    if (enabled.size <= max) NavLayout(enabled, emptyList()) else NavLayout(enabled.take(max), enabled.drop(max))
