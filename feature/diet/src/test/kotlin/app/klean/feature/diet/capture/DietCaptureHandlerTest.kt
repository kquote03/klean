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

    @Test fun suggestionsAreQuickRepeats() = runBlocking {
        repo.repeats = listOf("tea", "toast")
        assertEquals(listOf("tea", "toast"), handler.suggestions())
    }
}
