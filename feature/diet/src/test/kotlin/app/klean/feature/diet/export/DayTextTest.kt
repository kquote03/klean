package app.klean.feature.diet.export

import app.klean.feature.diet.ui.DayCalories
import app.klean.feature.diet.ui.EntryKcal
import app.klean.shared.diet.KcalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DayTextTest {
    private val time: (Long) -> String = { "%02d:%02d".format(it / 60, it % 60) }
    private val label = "Wednesday, 11 November 2026"
    private val lines = listOf(
        DayLine(13 * 60 + 10, "salad", null),
        DayLine(9 * 60, "oatmeal", EntryKcal(150, false, KcalSource.USER)),
        DayLine(11 * 60 + 27, "banana", EntryKcal(105, true, KcalSource.HISTORY)),
        DayLine(11 * 60 + 45, "McDonald's cheeseburger", EntryKcal(300, false, KcalSource.USER)),
    )
    private val total = DayCalories(555, estimated = true, missing = 1)

    @Test fun theFormatInThePlan() {
        assertEquals(
            """
            What I ate on Wednesday, 11 November 2026
            09:00  oatmeal: 150 kcal
            11:27  banana: ≈ 105 kcal
            11:45  McDonald's cheeseburger: 300 kcal
            13:10  salad
            Total: ≈ 555 kcal (goal 2,000 kcal) · 1 food without calories
            """.trimIndent(),
            dayText(lines, total, goal = 2000, showCalories = true, time = time, dayLabel = label),
        )
    }

    @Test fun noCaloriesWhenTheyAreOff() {
        val t = dayText(lines, total, goal = 2000, showCalories = false, time = time, dayLabel = label)
        assertFalse("kcal" in t)
        assertFalse("Total" in t)
        assertEquals("09:00  oatmeal", t.lines()[1])
    }

    @Test fun anEmptyDay() {
        assertEquals("What I ate on $label\n(nothing logged)", dayText(emptyList(), DayCalories(0, false, 0), 0, true, time, label))
    }

    @Test fun noGoalAndNothingMissing() {
        val t = dayText(lines.take(2).drop(1), DayCalories(150, false, 0), goal = 0, showCalories = true, time = time, dayLabel = label)
        assertEquals("Total: 150 kcal", t.lines().last())
    }

    /** The amount travels with the food, calories or not: an assistant can't estimate "rice" without it. */
    @Test fun theAmountIsPartOfTheLine() {
        val t = dayText(
            listOf(DayLine(540L, "rice", null, grams = 200)),
            DayCalories(0, false, 1), goal = 0, showCalories = false, time = time, dayLabel = label,
        )
        assertEquals("09:00  rice (200 g)", t.lines()[1])
    }
}
