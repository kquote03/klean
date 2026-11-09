package app.klean.core.ui.module

/**
 * Today's cards: default slot order, then the user's saved order for the ids it mentions; hidden ids
 * removed. A module the saved order doesn't know yet (new in an update) lands after the saved ones,
 * in slot order.
 */
fun <T> arrangeCards(cards: List<T>, id: (T) -> String, slot: (T) -> Int, saved: List<String>, hidden: Set<String>): List<T> {
    val visible = cards.filter { id(it) !in hidden }.sortedBy(slot)
    if (saved.isEmpty()) return visible
    val rank = saved.withIndex().associate { (i, v) -> v to i }
    return visible.sortedWith(compareBy({ rank[id(it)] ?: (saved.size + slot(it)) }, slot))
}
