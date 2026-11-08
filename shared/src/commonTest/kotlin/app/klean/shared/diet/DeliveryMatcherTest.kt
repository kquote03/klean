package app.klean.shared.diet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeliveryMatcherTest {
    @Test fun deliveredTexts() = listOf(
        "Your order has been delivered", "Rider has arrived", "Your food is here", "Enjoy your meal!", "Driver is at your door",
    ).forEach { assertTrue(DeliveryMatcher.isDelivered(it), it) }

    @Test fun notYetDeliveredOrPromo() = listOf(
        "Your order is on the way", "Out for delivery", "Arriving in 5 minutes", "Restaurant is preparing your order",
        "50% off delivered to you today",
    ).forEach { assertFalse(DeliveryMatcher.isDelivered(it), it) }
}
