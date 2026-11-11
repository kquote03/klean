package app.klean.core.design

import app.klean.core.design.component.lineLimit
import org.junit.Assert.assertEquals
import org.junit.Test

/** A single long word ends in "…" on one line instead of breaking mid-word at font scale 1.5 (UI Phase 3 open item). */
class LineLimitTest {
    @Test fun aSingleWordGetsOneLine() {
        assertEquals(1, lineLimit("Yesterday", 2))
        assertEquals(1, lineLimit("  Donaudampfschifffahrt ", 3))
    }

    @Test fun wordsKeepTheirLines() {
        assertEquals(2, lineLimit("Push day", 2))
        assertEquals(3, lineLimit("Sunday into Monday", 3))
    }
}
