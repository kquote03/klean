package app.klean.today

/** Today's two buttons that aren't modules. */
const val SPEAK_KEY = "speak"
const val TYPE_KEY = "type"
private const val FOOD_KEY = "diet"

/** Where Food goes by default: index 4 in reach order is the tile right above Speak (see thumbRows). */
private const val FOOD_DEFAULT_REACH = 4

/**
 * Today's buttons in reach order (nearest the thumb first) from the [available] keys (Speak, Type, then the modules
 * in Arrange Today order) and the user's [saved] order. Saved keys that are no longer available are dropped; new
 * ones keep their default place. With nothing saved: Speak, Type, the modules, with Food right above Speak.
 */
fun quickButtonOrder(available: List<String>, saved: List<String>): List<String> {
    if (saved.isEmpty()) return defaultQuickOrder(available)
    val kept = saved.filter { it in available }
    val defaults = defaultQuickOrder(available)
    val result = kept.toMutableList()
    defaults.filter { it !in kept }.forEach { key -> result.add(defaults.indexOf(key).coerceAtMost(result.size), key) }
    return result
}

fun defaultQuickOrder(available: List<String>): List<String> {
    val base = available.toMutableList()
    if (base.remove(FOOD_KEY)) base.add(FOOD_DEFAULT_REACH.coerceAtMost(base.size), FOOD_KEY)
    return base
}

/** [order] with the keys [a] and [b] swapped (the arrange mode: tap one button, then another). */
fun swapKeys(order: List<String>, a: String, b: String): List<String> =
    order.map { when (it) { a -> b; b -> a; else -> it } }
