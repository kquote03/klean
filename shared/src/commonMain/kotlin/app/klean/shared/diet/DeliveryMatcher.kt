package app.klean.shared.diet

object DeliveryMatcher {
    val DEFAULT_APPS = listOf("com.talabat", "com.deliveroo.orderapp", "com.careem.acma", "com.noon.buyerapp")
    private val positive = Regex("""(?i)\b(delivered|has arrived|have arrived|is here|at your door|enjoy your (meal|food|order))\b""")
    private val negative = Regex("""(?i)(on the way|out for delivery|arriving|preparing|\d+\s*%|\boff\b|promo|discount)""")
    fun isDelivered(text: String): Boolean = positive.containsMatchIn(text) && !negative.containsMatchIn(text)
}
