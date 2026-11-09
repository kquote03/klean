package app.klean.feature.diet.delivery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.prefs.DevicePrefs
import app.klean.feature.diet.data.DietRepository
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.shared.time.HOUR_MS
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** When the one-time delivery offer appears on the Diet screen (Task 12.2). Pure. */
object DeliveryOfferPolicy {
    const val MIN_ENTRIES = 3
    const val SNOOZE_MS = 30 * 24 * HOUR_MS
    const val LOOKBACK_MS = 30 * 24 * HOUR_MS

    private val hint = Regex("""(?i)\b(talabat|deliveroo|careem|noon|delivery|delivered|takeaway|take-away|takeout|ordered)\b""")

    /** An entry that sounds like it came from a delivery app ("talabat burger", "ordered sushi"). */
    fun looksLikeDelivery(text: String): Boolean = hint.containsMatchIn(text)

    /**
     * [entryCount]: entries in the last 30 days. [deliveryHint]: a delivery app is installed or
     * something logged sounds like a delivery. [accessGranted]: notification access is already on.
     */
    fun shouldOffer(state: String, now: Long, entryCount: Int, deliveryHint: Boolean, accessGranted: Boolean): Boolean {
        if (accessGranted || entryCount < MIN_ENTRIES || !deliveryHint) return false
        return when {
            state == DeliveryOffer.NOT_OFFERED -> true
            state.startsWith(DeliveryOffer.SNOOZED_PREFIX) ->
                state.removePrefix(DeliveryOffer.SNOOZED_PREFIX).toLongOrNull()?.let { now >= it } ?: false
            else -> false
        }
    }
}

/** What the offer needs to know about the phone. An interface so tests choose. */
interface DeliveryEnvironment {
    fun anyInstalled(packages: Collection<String>): Boolean
    fun accessGranted(): Boolean
}

class AndroidDeliveryEnvironment @Inject constructor(@ApplicationContext private val ctx: Context) : DeliveryEnvironment {
    // Visible through the manifest's <queries> for the built-in apps.
    override fun anyInstalled(packages: Collection<String>) = packages.any { pkg ->
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    override fun accessGranted() = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)
}

/** The offer card's state and answers. */
interface DeliveryOffers {
    val visible: Flow<Boolean>
    suspend fun accept()
    suspend fun snooze()
    suspend fun decline()
}

class DefaultDeliveryOffers @Inject constructor(
    private val prefs: DevicePrefs,
    private val repo: DietRepository,
    private val clock: Clock,
    private val env: DeliveryEnvironment,
    private val settings: SettingsRepository,
) : DeliveryOffers {
    private fun now() = clock.now().toEpochMilliseconds()

    override val visible: Flow<Boolean> = combine(
        prefs.deliveryOffer,
        repo.observeEatenTimes(now() - DeliveryOfferPolicy.LOOKBACK_MS, now() + HOUR_MS),
        repo.observeDay(repo.todayDate()),
        settings.observe(SyncedKeys.DietDeliveryApps),
    ) { state, times, today, apps ->
        // The user's own allowlist (the built-in apps unless edited), not the debug-only extras.
        val hint = today.any { DeliveryOfferPolicy.looksLikeDelivery(it.rawText) } || env.anyInstalled(apps)
        DeliveryOfferPolicy.shouldOffer(state, now(), times.size, hint, env.accessGranted())
    }

    override suspend fun accept() = prefs.setDeliveryOffer(DeliveryOffer.ACCEPTED)
    override suspend fun snooze() = prefs.setDeliveryOffer(DeliveryOffer.snoozedUntil(now() + DeliveryOfferPolicy.SNOOZE_MS))
    override suspend fun decline() = prefs.setDeliveryOffer(DeliveryOffer.DECLINED)
}

@HiltViewModel
class DeliveryOfferViewModel @Inject constructor(private val offers: DeliveryOffers) : ViewModel() {
    val visible: StateFlow<Boolean> = offers.visible.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun accept(): Job = viewModelScope.launch { offers.accept() }
    fun snooze(): Job = viewModelScope.launch { offers.snooze() }
    fun decline(): Job = viewModelScope.launch { offers.decline() }
}

/** The system screen that turns notification access on for Klean. */
object DeliveryIntents {
    fun listenerSettings(ctx: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(ctx, DeliveryListenerService::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        val resolvable = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        return (if (resolvable) intent else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeliveryOfferModule {
    @Binds abstract fun offers(impl: DefaultDeliveryOffers): DeliveryOffers
    @Binds abstract fun env(impl: AndroidDeliveryEnvironment): DeliveryEnvironment
}
