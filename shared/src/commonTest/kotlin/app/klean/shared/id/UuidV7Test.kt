package app.klean.shared.id

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidV7Test {
    @Test fun formatAndVersion() {
        val id = UuidV7.generate(1_762_600_000_000L, Random(1))
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id), id)
    }
    @Test fun timestampPrefixSortsByTime() {
        val a = UuidV7.generate(1_000L, Random(1)); val b = UuidV7.generate(2_000L, Random(1))
        assertTrue(a < b)
    }
    @Test fun extractsTimestamp() { assertEquals(1_762_600_000_123L, UuidV7.timestampOf(UuidV7.generate(1_762_600_000_123L))) }
    @Test fun unique() { assertEquals(10_000, List(10_000) { UuidV7.generate(5L) }.toSet().size) }
}
