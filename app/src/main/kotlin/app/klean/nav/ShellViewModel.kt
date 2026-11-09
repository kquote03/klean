package app.klean.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.onboarding.OnboardingState
import app.klean.feature.settings.quiet.QuietHoursStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hands app-wide singletons to screens, so the shell can be built in tests without Hilt. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    val registry: ModuleRegistry,
    val capture: UniversalCaptureService,
    val clock: Clock,
    val permissions: PermissionChecker,
    val onboarding: OnboardingState,
    val quietHours: QuietHoursStore,
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
