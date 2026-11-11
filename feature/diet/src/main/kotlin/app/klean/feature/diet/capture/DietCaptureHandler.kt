package app.klean.feature.diet.capture

import app.klean.feature.diet.data.DietRepository
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureHandler
import app.klean.shared.capture.CaptureInput
import app.klean.shared.module.ModuleId
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/** Diet's part of universal capture: anything routed here is logged as food, as written. */
class DietCaptureHandler @Inject constructor(private val repo: DietRepository) : CaptureHandler {
    override val moduleId: ModuleId = ModuleId.DIET
    override val actionSpecs: List<ActionSpec> = listOf(ActionSpec(LOG_FOOD, "Something the user ate or drank"))

    override fun defaultAction(input: CaptureInput) = CandidateAction(moduleId, LOG_FOOD, 1f)

    override suspend fun execute(input: CaptureInput, action: CandidateAction): String =
        repo.logEntry(
            input.text, input.source, eatenAt = input.eatenAt, photoFile = input.photoFile, viaVoice = input.viaVoice,
            calories = input.extras[EXTRA_KCAL],
        )

    override suspend fun undo(recordId: String) = repo.delete(recordId)

    override suspend fun suggestions(): List<String> = repo.quickRepeats()

    companion object {
        const val LOG_FOOD = "log_food"
        /** The capture sheet's calorie slot. */
        const val EXTRA_KCAL = app.klean.shared.diet.CalorieInput.EXTRA_KEY
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DietCaptureModule {
    @Binds @IntoSet abstract fun handler(h: DietCaptureHandler): CaptureHandler
}
