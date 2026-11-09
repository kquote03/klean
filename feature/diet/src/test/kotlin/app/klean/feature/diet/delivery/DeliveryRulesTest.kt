package app.klean.feature.diet.delivery

import app.klean.shared.diet.DeliveryMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §6.2 / Task 12.1: only allowlisted packages are read at all, and only "delivered" counts. */
class DeliveryRulesTest {
    private val rules = DeliveryRules(DeliveryMatcher.DEFAULT_APPS.toSet(), words = emptyList())

    @Test fun otherPackagesAreNeverRead() {
        var reads = 0
        val text = { reads++; "Your order has been delivered" }
        assertFalse(rules.isDelivery("com.whatsapp", text))
        assertFalse(rules.isDelivery("com.android.shell", text))
        assertEquals(0, reads)
    }

    @Test fun allowlistedDeliveredCounts() {
        var reads = 0
        assertTrue(rules.isDelivery("com.talabat") { reads++; "Your order has been delivered. Enjoy your meal" })
        assertEquals(1, reads)
    }

    @Test fun onTheWayAndPromosDoNot() {
        assertFalse(rules.isDelivery("com.talabat") { "Your order is on the way" })
        assertFalse(rules.isDelivery("com.deliveroo.orderapp") { "20% off today only" })
        assertFalse(rules.isDelivery("com.noon.buyerapp") { "" })
    }

    @Test fun userWordsAlsoCountCaseInsensitively() {
        val custom = DeliveryRules(setOf("com.example.food"), words = listOf("  Rider dropped  ", ""))
        assertTrue(custom.isDelivery("com.example.food") { "The rider dropped off your bag" })
        assertFalse(custom.isDelivery("com.example.food") { "Order confirmed" })
    }

    @Test fun debugExtrasJoinTheSettingsList() {
        val r = DeliveryRules.of(apps = listOf("com.talabat", " "), words = listOf("x"), extraPackages = setOf("com.android.shell"))
        assertEquals(setOf("com.talabat", "com.android.shell"), r.packages)
        assertEquals(listOf("x"), r.words)
    }
}
