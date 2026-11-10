package app.klean.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.common.ZoneProvider
import app.klean.core.data.module.ModuleGate
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.TodayItems
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.shared.module.ModuleId
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.OnboardingStepProvider
import app.klean.feature.onboarding.OnboardingState
import app.klean.feature.settings.quiet.QuietHoursStore
import app.klean.today.InMemoryTodayLayout
import app.klean.today.TodayLayoutStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** Hands app-wide singletons to screens, so the shell can be built in tests without Hilt. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    val registry: ModuleRegistry,
    val capture: UniversalCaptureService,
    val clock: Clock,
    val permissions: PermissionChecker,
    val onboarding: OnboardingState,
    val quietHours: QuietHoursStore,
    // Defaults keep non-Hilt shell tests short; Hilt always uses the full constructor.
    val dashboard: DashboardContributions = DashboardContributions(emptySet(), emptySet(), NoGate),
    val zone: ZoneProvider = SystemZone,
    val ticker: MinuteTicker = MinuteTicker(),
    val todayLayout: TodayLayoutStore = InMemoryTodayLayout(),
    val newModules: NewModulesStore = InMemoryNewModules(),
    val groupMemory: NavGroupMemory = InMemoryNavGroupMemory(),
    val todayItems: TodayItems = TodayItems(emptySet(), NoGate),
    val onboardingSteps: Set<@JvmSuppressWildcards OnboardingStepProvider> = emptySet(),
) : ViewModel() {
    private val _startWithSetup = MutableStateFlow<Boolean?>(null)

    /** Whether the app opens on the setup wizard; null until known (a single DataStore/DB read). */
    val startWithSetup: StateFlow<Boolean?> = _startWithSetup.asStateFlow()

    init {
        viewModelScope.launch {
            // If the check itself fails, open the app: setup is optional and can be run from Settings.
            _startWithSetup.value = runCatching { onboarding.shouldShowWizard() }.getOrDefault(false)
        }
    }
}

private object NoGate : ModuleGate {
    override val disabledIds: Flow<Set<ModuleId>> = flowOf(emptySet())
    override suspend fun isEnabled(id: ModuleId) = true
    override suspend fun setEnabled(id: ModuleId, enabled: Boolean) {}
}

private object SystemZone : ZoneProvider {
    override fun current() = TimeZone.currentSystemDefault()
}
