package app.klean.feature.diet.delivery

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.core.data.prefs.DevicePrefs
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The listener's decision, free of Android service plumbing (tested with fakes). Text is read only
 * for allowlisted packages, matched in memory, and dropped; only the package name goes further.
 */
class DeliveryNotificationHandler(
    private val rules: DeliveryRulesSource,
    private val prompts: DeliveryPrompts,
    private val scope: CoroutineScope,
    private val ownPackage: String,
) {
    @Inject constructor(
        rules: DeliveryRulesSource,
        prompts: DeliveryPrompts,
        @ApplicationScope scope: CoroutineScope,
        @ApplicationContext ctx: Context,
    ) : this(rules, prompts, scope, ctx.packageName)

    /** Returns whether this notification counted as a delivery. */
    fun onPosted(pkg: String, text: () -> String): Boolean {
        if (pkg == ownPackage) return false
        if (!rules.current().isDelivery(pkg, text)) return false
        scope.launch {
            // Package name only, never the notification's text.
            runCatching { prompts.onDelivered(pkg) }
                .onSuccess { Log.i("Klean", "delivery from $pkg, asked=$it") }
                .onFailure { Log.w("Klean", "delivery prompt failed", it) }
        }
        return true
    }
}

/**
 * Notification access, granted by the user from the Diet screen's one-time offer (Task 12.2) or the
 * system settings. Granting access is the user's yes, so connecting records ACCEPTED.
 */
@AndroidEntryPoint
class DeliveryListenerService : NotificationListenerService() {
    @Inject lateinit var handler: DeliveryNotificationHandler
    @Inject lateinit var prefs: DevicePrefs
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onListenerConnected() {
        Log.i("Klean", "delivery listener connected")
        scope.launch { prefs.setDeliveryOffer(DeliveryOffer.ACCEPTED) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        handler.onPosted(pkg) { DeliveryText.of(sbn.notification) }
    }
}
