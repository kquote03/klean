package app.klean.feature.onboarding

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Whether the setup wizard should run, and the way to say it has. An interface so the shell can be tested. */
interface OnboardingState {
    suspend fun shouldShowWizard(): Boolean
    suspend fun markDone()
}

/** Does this install already hold the user's data? Then it isn't a first run, whatever the flag says. */
fun interface ExistingDataProbe {
    suspend fun hasData(): Boolean
}

/**
 * The wizard runs once, on a fresh install. An install that already has entries (an update from a
 * build without setup, or a restored backup) is marked done the first time it is asked, so the
 * wizard never stands between someone and the data they already have.
 */
@Singleton
class OnboardingGate @Inject constructor(
    private val prefs: DevicePrefs,
    private val probe: ExistingDataProbe,
) : OnboardingState {
    override suspend fun shouldShowWizard(): Boolean {
        if (prefs.onboardingDone.first()) return false
        if (probe.hasData()) {
            prefs.setOnboardingDone(true)
            return false
        }
        return true
    }

    override suspend fun markDone() = prefs.setOnboardingDone(true)
}

class FoodEntryDataProbe @Inject constructor(private val db: KleanDatabase) : ExistingDataProbe {
    override suspend fun hasData(): Boolean = db.foodEntryDao().count() > 0
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingBindings {
    @Binds abstract fun state(impl: OnboardingGate): OnboardingState
    @Binds abstract fun probe(impl: FoodEntryDataProbe): ExistingDataProbe
}
