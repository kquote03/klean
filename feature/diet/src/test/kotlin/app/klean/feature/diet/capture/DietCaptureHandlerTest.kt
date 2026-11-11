package app.klean.feature.diet.capture

import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.shared.capture.CaptureInput
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DietCaptureHandlerTest {
    private val clock = FakeClock(1_800_000_000_000)
    private val repo = FakeDietRepository(clock)
    private val handler = DietCaptureHandler(repo)

    @Test fun executeLogsTextWithItsSource() = runBlocking {
        val input = CaptureInput("toast", CaptureSource.QS_TILE, clock.nowMs)
        val id = handler.execute(input, handler.defaultAction(input))
        assertEquals(listOf(FakeDietRepository.LogCall("toast", CaptureSource.QS_TILE, null)), repo.logs)
        assertEquals(ModuleId.DIET, handler.defaultAction(input).moduleId)
        handler.undo(id)
        assertEquals(listOf(id), repo.deletes)
    }

    @Test fun presetEatenAtAndVoiceAndPhotoReachTheRepository() = runBlocking {
        val input = CaptureInput("rice", CaptureSource.MODULE_SCREEN, clock.nowMs, viaVoice = true, photoFile = "p.jpg", eatenAt = 1_799_990_000_000)
        handler.execute(input, handler.defaultAction(input))
        assertEquals(
            listOf(FakeDietRepository.LogCall("rice", CaptureSource.MODULE_SCREEN, 1_799_990_000_000, viaVoice = true, photoFile = "p.jpg")),
            repo.logs,
        )
    }

    @Test fun theCalorieSlotReachesTheRepository() = runBlocking {
        val input = CaptureInput("chips", CaptureSource.MODULE_SCREEN, clock.nowMs, extras = mapOf(DietCaptureHandler.EXTRA_KCAL to "150x100x200"))
        handler.execute(input, handler.defaultAction(input))
        assertEquals("150x100x200", repo.logs.single().calories)
        assertEquals("diet.kcal", DietCaptureHandler.EXTRA_KCAL)
    }

    @Test fun suggestionsAreQuickRepeats() = runBlocking {
        repo.repeats = listOf("tea", "toast")
        assertEquals(listOf("tea", "toast"), handler.suggestions())
    }
}
