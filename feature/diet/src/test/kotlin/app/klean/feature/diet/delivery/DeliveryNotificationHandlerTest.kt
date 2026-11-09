package app.klean.feature.diet.delivery

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.shared.diet.DeliveryMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The listener's decision, with fakes: which notifications are read, and which lead to a prompt. */
@RunWith(AndroidJUnit4::class)
class DeliveryNotificationHandlerTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val delivered = mutableListOf<String>()
    private var rules = DeliveryRules(DeliveryMatcher.DEFAULT_APPS.toSet() + "com.android.shell", emptyList())
    private val handler = DeliveryNotificationHandler(
        rules = DeliveryRulesSource { rules },
        prompts = DeliveryPrompts { pkg -> delivered += pkg; true },
        scope = CoroutineScope(Dispatchers.Unconfined),
        ownPackage = "app.klean.debug",
    )

    private fun notification(title: String, text: String, big: String? = null): Notification =
        NotificationCompat.Builder(ctx, "x").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .apply { big?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
            .build()

    @Test fun deliveredFromAnAllowlistedAppPrompts() {
        assertTrue(handler.onPosted("com.talabat") { DeliveryText.of(notification("Talabat", "Your order has been delivered")) })
        assertEquals(listOf("com.talabat"), delivered)
    }

    @Test fun otherAppsAreNotReadEvenWithDeliveryWords() {
        var reads = 0
        assertFalse(handler.onPosted("com.whatsapp") { reads++; "Your order has been delivered" })
        assertEquals(0, reads)
        assertTrue(delivered.isEmpty())
    }

    @Test fun ownNotificationsAreIgnored() {
        rules = rules.copy(packages = rules.packages + "app.klean.debug")
        var reads = 0
        assertFalse(handler.onPosted("app.klean.debug") { reads++; "delivered" })
        assertEquals(0, reads)
    }

    @Test fun onTheWayDoesNotPrompt() {
        assertFalse(handler.onPosted("com.android.shell") { DeliveryText.of(notification("Talabat", "Your order is on the way")) })
        assertTrue(delivered.isEmpty())
    }

    @Test fun textJoinsTitleTextAndBigText() {
        val t = DeliveryText.of(notification("Talabat", "Update", big = "Your order has arrived"))
        assertTrue(t, t.contains("Talabat") && t.contains("Update") && t.contains("Your order has arrived"))
    }
}
