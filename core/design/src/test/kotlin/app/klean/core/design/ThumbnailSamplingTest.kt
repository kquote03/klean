package app.klean.core.design

import app.klean.core.design.component.Thumbnails
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailSamplingTest {
    @Test fun sampleKeepsShortEdgeAtOrAboveTarget() {
        // 1600x1200 photo, 144 px thumbnail (48 dp at xxhdpi): 1200/8 = 150 >= 144, 1200/16 = 75 < 144.
        assertEquals(8, Thumbnails.sampleSize(1600, 1200, 144))
        assertEquals(1, Thumbnails.sampleSize(100, 100, 144))
        assertEquals(1, Thumbnails.sampleSize(0, 0, 144))
        assertEquals(4, Thumbnails.sampleSize(1200, 1600, 300))
    }
}
