package app.klean.core.capture

import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTileModelTest {
    @Test fun onlyDietStillSaysAddToKlean() =
        assertEquals(CaptureTileModel(true, R.string.tile_label), CaptureTileModel.from(listOf(ModuleId.DIET)))

    @Test fun severalModulesSayAddToKlean() =
        assertEquals(CaptureTileModel(true, R.string.tile_label), CaptureTileModel.from(listOf(ModuleId.DIET, ModuleId.TASKS)))

    @Test fun noModuleMakesTileUnavailable() =
        assertEquals(false, CaptureTileModel.from(emptyList()).available)
}
