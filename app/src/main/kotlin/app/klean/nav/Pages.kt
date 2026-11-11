package app.klean.nav

import kotlin.reflect.KClass

/**
 * One page of the shell's pager: a top-level screen ([route]: Today, Track's Week, a module's start route), the bar
 * [group] it belongs to and its [tab] index within that group's tabs.
 */
data class Page(val route: Any, val group: NavGroup, val tab: Int) {
    /** Stable across modules turning on and off: the route's class name. */
    val key: String get() = keyOf(route::class)

    companion object {
        fun keyOf(route: KClass<*>): String = route.java.name
    }
}

/**
 * The pages left to right: Today, then each group's tabs in bar order ([tabs]: a group and the start routes of its
 * tabs, modules that are off already left out). Today comes first whether or not [tabs] lists it.
 */
fun pageList(tabs: List<Pair<NavGroup, List<Any>>>): List<Page> =
    listOf(Page(TodayRoute, NavGroup.TODAY, 0)) +
        tabs.filter { it.first != NavGroup.TODAY }.flatMap { (g, routes) -> routes.mapIndexed { i, r -> Page(r, g, i) } }

/** The index of the page showing [route] (a route class), or -1 when it isn't a page (a detail, a module that is off). */
fun List<Page>.indexOfRoute(route: KClass<*>): Int = indexOfFirst { it.route::class == route }

/** The index of [group]'s tab [tab], or -1. */
fun List<Page>.indexOfTab(group: NavGroup, tab: Int): Int = indexOfFirst { it.group == group && it.tab == tab }

/** How many tabs [group] has among the pages. */
fun List<Page>.tabCount(group: NavGroup): Int = count { it.group == group }

/**
 * How the pager gets from page [from] to page [to] when a bar item or a tab asks: a neighbour slides over; anything
 * further jumps with a short cross-fade rather than flying past every page between.
 */
fun slidesTo(from: Int, to: Int): Boolean = kotlin.math.abs(to - from) <= 1
