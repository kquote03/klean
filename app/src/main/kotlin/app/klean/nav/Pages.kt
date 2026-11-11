package app.klean.nav

import kotlin.math.abs

/**
 * The page a horizontal swipe of [dx] pixels leads to from [current], in the left-to-right [pages] order (Today, then
 * Plan's tabs, Track's tabs, People's tabs). A swipe to the left (negative [dx]) shows the next page, to the right the
 * previous one. Null at either end, for no movement, or when [current] isn't a page (a detail screen).
 */
fun <T> pageAfter(pages: List<T>, current: T?, dx: Float): T? {
    val i = pages.indexOf(current)
    if (i < 0 || dx == 0f) return null
    return pages.getOrNull(if (dx < 0f) i + 1 else i - 1)
}

/** A finished gesture is a page swipe when it travelled at least [minPx] sideways and more than twice as far as up or down. */
fun isPageSwipe(dx: Float, dy: Float, minPx: Float): Boolean = abs(dx) >= minPx && abs(dx) > 2 * abs(dy)

/**
 * Whether going from page [from] to page [to] moves right through [order] (the new page comes in from the right). Null
 * when either isn't a page.
 */
fun <T> pageForward(order: List<T>, from: T?, to: T?): Boolean? {
    val a = order.indexOf(from)
    val b = order.indexOf(to)
    return if (a < 0 || b < 0 || a == b) null else b > a
}
