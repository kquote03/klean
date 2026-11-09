package app.klean.feature.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.ui.module.KleanModule
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.settings.quiet.QuietHoursStore
import app.klean.shared.module.ModuleId
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The setup wizard's destination. */
@Serializable object OnboardingRoute

/** One page of the wizard. Which pages exist depends on the registered and enabled modules. */
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Modules : OnboardingStep
    data object SleepWindow : OnboardingStep
    data class ModuleSetup(val id: ModuleId) : OnboardingStep
    data object Widget : OnboardingStep
    data object Permissions : OnboardingStep
    data object Ai : OnboardingStep
}

data class ModuleChoice(val id: ModuleId, @param:StringRes val labelRes: Int, val enabled: Boolean)

data class OnboardingUiState(
    val steps: List<OnboardingStep>,
    val current: OnboardingStep = OnboardingStep.Welcome,
    /** The sleep window being edited; saved only when the sleep page is accepted with Next. */
    val sleep: QuietHours = QuietHours.DEFAULT,
    val modules: List<ModuleChoice> = emptyList(),
    val finished: Boolean = false,
) {
    /** 1-based, for "2 of 7". */
    val position: Int get() = steps.indexOf(current) + 1
    val isLast: Boolean get() = position == steps.size
}

/**
 * Wizard state and skip logic. Every page can be skipped; Skip never writes anything. Next on the
 * sleep page saves the window; module and permission pages write as their switches change.
 * Finishing (or skipping the rest) marks setup done.
 */
class OnboardingViewModel(
    private val registry: ModuleRegistry,
    private val quietHours: QuietHoursStore,
    private val onboarding: OnboardingState,
    /** App-level widgets (the holistic Today widget), offered before the modules' own. */
    private val appWidgets: List<Class<*>> = emptyList(),
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState(steps = stepsFor(registry.all, registry.all, appWidgets.isNotEmpty())))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var sleepEdited = false

    init {
        viewModelScope.launch {
            registry.enabled.collect { on ->
                val onIds = on.map { it.id }.toSet()
                _state.update { s ->
                    val steps = stepsFor(registry.all, on, appWidgets.isNotEmpty())
                    val current = if (s.current in steps) s.current else steps[(s.position - 1).coerceIn(0, steps.lastIndex)]
                    s.copy(
                        steps = steps,
                        current = current,
                        modules = registry.all.map { ModuleChoice(it.id, it.labelRes, it.id in onIds) },
                    )
                }
            }
        }
        viewModelScope.launch {
            val saved = quietHours.quietHours.first()
            if (!sleepEdited) _state.update { it.copy(sleep = saved) }
        }
    }

    /** Module for a [OnboardingStep.ModuleSetup] page. */
    fun module(id: ModuleId): KleanModule? = registry.all.firstOrNull { it.id == id }

    /** The widget page's offers: app widgets first, then each enabled module's own. */
    fun widgetReceivers(): List<Class<*>> =
        appWidgets + _state.value.modules.filter { it.enabled }.mapNotNull { module(it.id)?.widgetReceiver }

    fun setModuleEnabled(id: ModuleId, enabled: Boolean) {
        viewModelScope.launch { registry.setEnabled(id, enabled) }
    }

    fun setSleepStart(minute: Int) {
        sleepEdited = true
        _state.update { it.copy(sleep = it.sleep.copy(startMinute = minute)) }
    }

    fun setSleepEnd(minute: Int) {
        sleepEdited = true
        _state.update { it.copy(sleep = it.sleep.copy(endMinute = minute)) }
    }

    /** Accept this page and go on. */
    fun next() {
        val s = _state.value
        if (s.current == OnboardingStep.SleepWindow) {
            val chosen = s.sleep
            viewModelScope.launch { quietHours.set(chosen) }
        }
        advance()
    }

    /** Leave this page as it is and go on. */
    fun skip() = advance()

    /** Skip the whole wizard. */
    fun skipAll() = finish()

    fun back() {
        _state.update { s ->
            val i = s.steps.indexOf(s.current)
            if (i <= 0) s else s.copy(current = s.steps[i - 1])
        }
    }

    private fun advance() {
        val s = _state.value
        if (s.finished) return
        val i = s.steps.indexOf(s.current)
        if (i >= s.steps.lastIndex) finish() else _state.update { it.copy(current = s.steps[i + 1]) }
    }

    private fun finish() {
        if (_state.value.finished) return
        viewModelScope.launch {
            onboarding.markDone()
            _state.update { it.copy(finished = true) }
        }
    }

    companion object {
        fun stepsFor(all: List<KleanModule>, enabled: List<KleanModule>, appWidgets: Boolean = false): List<OnboardingStep> = buildList {
            add(OnboardingStep.Welcome)
            if (all.isNotEmpty()) add(OnboardingStep.Modules)
            add(OnboardingStep.SleepWindow)
            enabled.filter { it.onboardingTitleRes != null }.forEach { add(OnboardingStep.ModuleSetup(it.id)) }
            if (appWidgets || enabled.any { it.widgetReceiver != null }) add(OnboardingStep.Widget)
            add(OnboardingStep.Permissions)
            add(OnboardingStep.Ai)
        }
    }
}
