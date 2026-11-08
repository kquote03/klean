package app.klean.shared.diet.prompt

import app.klean.shared.time.atMinute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealTimeLearnerTest {
    private val zone = TimeZone.of("Asia/Dubai")
    private fun t(h: Int, m: Int = 0, day: Int, month: Int = 11) = atMinute(LocalDate(2026, month, day), h * 60 + m, zone)
    private val now = t(12, day = 20)

    private fun meals(days: IntRange) = days.flatMap { d -> listOf(t(8, 10, d), t(13, 20, d), t(20, 5, d)) }

    @Test fun sevenDaysOfThreeMeals_threeSlots() {
        val slots = MealTimeLearner.learn(meals(10..16), now, zone)
        assertEquals(3, slots.size, slots.toString())
        listOf(8 * 60 + 10, 13 * 60 + 20, 20 * 60 + 5).zip(slots).forEach { (expected, s) ->
            assertTrue(abs(s.centerMinute - expected) <= 30, "$s vs $expected")
            assertEquals((s.centerMinute + 45) % 1440, s.promptMinute)
        }
        assertEquals(listOf(MealLabel.BREAKFAST, MealLabel.LUNCH, MealLabel.DINNER), slots.map { it.label })
    }

    @Test fun sixDays_empty() = assertEquals(emptyList(), MealTimeLearner.learn(meals(10..15), now, zone))

    @Test fun randomSnacks_stillThreeSlots() {
        val snacks = listOf(9 to 47, 15 to 2, 17 to 31, 22 to 10, 11 to 44, 16 to 20, 23 to 5)
            .mapIndexed { i, (h, m) -> t(h, m, 10 + i) }
        val slots = MealTimeLearner.learn(meals(10..16) + snacks, now, zone)
        assertEquals(3, slots.size, slots.toString())
    }

    @Test fun midnightCluster_oneLateSlot() {
        val entries = (10..16).flatMap { d -> listOf(t(23, 50, d), t(0, 20, d)) }
        val slots = MealTimeLearner.learn(entries, now, zone)
        assertEquals(1, slots.size, slots.toString())
        assertEquals(MealLabel.LATE, slots.single().label)
    }

    @Test fun olderThanWindow_ignored() {
        val old = (1..7).flatMap { d -> listOf(t(8, 10, d, 10), t(13, 20, d, 10), t(20, 5, d, 10)) }
        assertEquals(emptyList(), MealTimeLearner.learn(old, now, zone))
    }
}
