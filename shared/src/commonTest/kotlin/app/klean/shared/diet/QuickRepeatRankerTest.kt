package app.klean.shared.diet

import app.klean.shared.time.HOUR_MS
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickRepeatRankerTest {
    private val now = 1_762_600_000_000L
    private val day = 24 * HOUR_MS

    @Test fun frequentThisWeekBeatsOnceYesterday() {
        val history = List(5) { RepeatCandidate("Oats", now - (it + 1) * day) } + RepeatCandidate("Pizza", now - day)
        assertEquals(listOf("Oats", "Pizza"), QuickRepeatRanker.rank(history, now))
    }
    @Test fun normalizesAndKeepsMostRecentCasing() {
        val history = listOf(RepeatCandidate("Shawarma", now - 2 * day), RepeatCandidate(" shawarma ", now - day))
        assertEquals(listOf("shawarma"), QuickRepeatRanker.rank(history, now))
    }
    @Test fun longTextsExcluded() {
        val history = listOf(RepeatCandidate("x".repeat(41), now), RepeatCandidate("tea", now), RepeatCandidate("   ", now))
        assertEquals(listOf("tea"), QuickRepeatRanker.rank(history, now))
    }
    @Test fun limitRespected() {
        val history = List(20) { RepeatCandidate("item $it", now - it * HOUR_MS) }
        assertEquals(3, QuickRepeatRanker.rank(history, now, limit = 3).size)
        assertEquals(8, QuickRepeatRanker.rank(history, now).size)
    }
    @Test fun emptyInput() = assertEquals(emptyList(), QuickRepeatRanker.rank(emptyList(), now))
}
