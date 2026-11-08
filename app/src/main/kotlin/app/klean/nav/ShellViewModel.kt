package app.klean.nav

import androidx.lifecycle.ViewModel
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.notifications.permissions.PermissionChecker
import app.klean.core.ui.module.ModuleRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock

/** Hands app-wide singletons to screens, so the shell can be built in tests without Hilt. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    val registry: ModuleRegistry,
    val capture: UniversalCaptureService,
    val clock: Clock,
    val permissions: PermissionChecker,
) : ViewModel()
