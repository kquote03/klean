package app.klean.feature.diet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.data.settings.SettingKey
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.notifications.AlarmScheduler
import app.klean.feature.diet.BuildConfig
import app.klean.feature.diet.notification.DietStatusLine
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

    /** Packages the delivery listener also reads, in debug builds only (`adb shell cmd notification post`). */
    val extraDeliveryPackages: Set<String> get() = emptySet()
}

class BuildConfigDietDebug @Inject constructor(private val alarms: AlarmScheduler) : DietDebug {
    override val enabled: Boolean = BuildConfig.DEBUG
    override val extraDeliveryPackages: Set<String> = if (BuildConfig.DEBUG) setOf(SHELL_PACKAGE) else emptySet()

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
    }
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
    @dagger.Provides fun refresh(line: DietStatusLine): DietStatusRefresh = DietStatusRefresh { line.refresh() }
}

data class DietSettingsState(
    val promptsEnabled: Boolean = true,
    val learnMealTimes: Boolean = true,
    val showCalories: Boolean = false,
    val statusNotification: Boolean = true,
    /** "Show today's list after a long break" (unlock sheet). */
    val unlockSheet: Boolean = true,
    /** Formatted time of the next check-in, or null when none is planned. */
    val nextCheckIn: String? = null,
    val debug: Boolean = false,
    val debugInfo: String? = null,
    /** Debug: the next unlock will count as the end of a long break. */
    val pretendArmed: Boolean = false,
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
        settings.observe(SyncedKeys.DietUnlockSheet),
    ) { prompts, learn, calories, statusLine, unlock -> listOf(prompts, learn, calories, statusLine, unlock) }

    val uiState: StateFlow<DietSettingsState> = combine(
        switches,
        prefs.nextPromptAt,
        debugInfo,
        prefs.unlockSheetPretend,
    ) { (prompts, learn, calories, statusLine, unlock), next, info, pretend ->
        DietSettingsState(
            promptsEnabled = prompts,
            learnMealTimes = learn,
            showCalories = calories,
            statusNotification = statusLine,
            unlockSheet = unlock,
            nextCheckIn = next?.takeIf { prompts }?.let(fmt::time),
            debug = debug.enabled,
            debugInfo = info,
            pretendArmed = debug.enabled && pretend,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DietSettingsState(debug = debug.enabled))

    fun setPromptsEnabled(v: Boolean) = setAndReplan(SyncedKeys.DietPromptsEnabled, v)
    fun setLearnMealTimes(v: Boolean) = setAndReplan(SyncedKeys.DietLearnMealTimes, v)
    fun setShowCalories(v: Boolean): Job = viewModelScope.launch { settings.set(SyncedKeys.DietShowCalories, v) }
    fun setStatusNotification(v: Boolean): Job = viewModelScope.launch {
        settings.set(SyncedKeys.DietStatusNotification, v)
        status.refresh()
    }

    fun setUnlockSheet(v: Boolean): Job = viewModelScope.launch { settings.set(SyncedKeys.DietUnlockSheet, v) }

    /** Debug only: the next unlock shows the sheet, as if the phone had been locked for 3 h. */
    fun pretendLongBreak(): Job = viewModelScope.launch { if (debug.enabled) prefs.setUnlockSheetPretend(true) }

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
