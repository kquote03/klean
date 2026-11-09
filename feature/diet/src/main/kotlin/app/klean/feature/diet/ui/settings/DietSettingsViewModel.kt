package app.klean.feature.diet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingKey
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.AlarmScheduler
import app.klean.feature.diet.BuildConfig
import app.klean.feature.diet.notification.DietStatusNotifier
import app.klean.feature.diet.prompt.DietPrompts
import app.klean.feature.diet.ui.DietFormatter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Debug-build switches for Diet settings. An interface so tests choose. */
interface DietDebug {
    val enabled: Boolean
    fun exactAlarmsAllowed(): Boolean
}

class BuildConfigDietDebug @Inject constructor(private val alarms: AlarmScheduler) : DietDebug {
    override val enabled: Boolean = BuildConfig.DEBUG
    override fun exactAlarmsAllowed() = alarms.canExact()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietDebugModule {
    @Binds abstract fun debug(impl: BuildConfigDietDebug): DietDebug
}

/** Re-posts (or removes) the status line after its setting changes. */
fun interface DietStatusRefresh {
    suspend fun refresh()
}

@Module
@InstallIn(SingletonComponent::class)
object DietStatusRefreshModule {
    @dagger.Provides fun refresh(notifier: DietStatusNotifier): DietStatusRefresh = DietStatusRefresh { notifier.refresh() }
}

data class DietSettingsState(
    val promptsEnabled: Boolean = true,
    val learnMealTimes: Boolean = true,
    val showCalories: Boolean = false,
    val statusNotification: Boolean = true,
    /** Formatted time of the next check-in, or null when none is planned. */
    val nextCheckIn: String? = null,
    val debug: Boolean = false,
    val debugInfo: String? = null,
)

@HiltViewModel
class DietSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val prefs: DevicePrefs,
    private val prompts: DietPrompts,
    private val fmt: DietFormatter,
    private val debug: DietDebug,
    private val status: DietStatusRefresh = DietStatusRefresh { },
) : ViewModel() {
    private val debugInfo = MutableStateFlow<String?>(null)

    private val switches = combine(
        settings.observe(SyncedKeys.DietPromptsEnabled),
        settings.observe(SyncedKeys.DietLearnMealTimes),
        settings.observe(SyncedKeys.DietShowCalories),
        settings.observe(SyncedKeys.DietStatusNotification),
    ) { prompts, learn, calories, statusLine -> listOf(prompts, learn, calories, statusLine) }

    val uiState: StateFlow<DietSettingsState> = combine(
        switches,
        prefs.nextPromptAt,
        debugInfo,
    ) { (prompts, learn, calories, statusLine), next, info ->
        DietSettingsState(
            promptsEnabled = prompts,
            learnMealTimes = learn,
            showCalories = calories,
            statusNotification = statusLine,
            nextCheckIn = next?.takeIf { prompts }?.let(fmt::time),
            debug = debug.enabled,
            debugInfo = info,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietSettingsState(debug = debug.enabled))

    fun setPromptsEnabled(v: Boolean) = setAndReplan(SyncedKeys.DietPromptsEnabled, v)
    fun setLearnMealTimes(v: Boolean) = setAndReplan(SyncedKeys.DietLearnMealTimes, v)
    fun setShowCalories(v: Boolean): Job = viewModelScope.launch { settings.set(SyncedKeys.DietShowCalories, v) }
    fun setStatusNotification(v: Boolean): Job = viewModelScope.launch {
        settings.set(SyncedKeys.DietStatusNotification, v)
        status.refresh()
    }

    fun fireNow(): Job = viewModelScope.launch { if (debug.enabled) prompts.fireNow() }

    fun showNext(): Job = viewModelScope.launch {
        if (!debug.enabled) return@launch
        val next = prefs.nextPromptAt.first()
        val exact = if (debug.exactAlarmsAllowed()) "exact" else "inexact"
        debugInfo.value = "next=${next?.let { "${fmt.time(it)} ($it)" } ?: "none"} · alarms=$exact"
    }

    private fun setAndReplan(key: SettingKey<Boolean>, v: Boolean): Job = viewModelScope.launch {
        settings.set(key, v)
        prompts.reschedule()
    }
}
