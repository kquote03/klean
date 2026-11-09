package app.klean.nav

import app.klean.feature.onboarding.OnboardingState
import app.klean.feature.settings.quiet.QuietHoursStore
import app.klean.shared.time.QuietHours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [show] = false is an install that has already been set up: the shell opens on Today. */
class FakeOnboardingState(var show: Boolean = false) : OnboardingState {
    var done = 0
    override suspend fun shouldShowWizard() = show
    override suspend fun markDone() { done++; show = false }
}

class FakeQuietHoursStore(initial: QuietHours = QuietHours.DEFAULT) : QuietHoursStore {
    val state = MutableStateFlow(initial)
    val writes = mutableListOf<QuietHours>()
    override val quietHours: Flow<QuietHours> = state
    override suspend fun set(value: QuietHours) { writes += value; state.value = value }
}
