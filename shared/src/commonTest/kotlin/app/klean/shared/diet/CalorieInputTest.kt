package app.klean.shared.diet

import kotlin.test.Test
import kotlin.test.assertEquals

class CalorieInputTest {
    @Test fun plainNumberIsKcal() {
        assertEquals(CalorieInput.Kcal(300), CalorieInput.parse("300"))
        assertEquals(CalorieInput.Kcal(300), CalorieInput.parse(" 300 kcal "))
        assertEquals(CalorieInput.Kcal(150), CalorieInput.parse("150cal"))
    }
    @Test fun gramsOnly() {
        assertEquals(CalorieInput.Grams(200), CalorieInput.parse("200g"))
        assertEquals(CalorieInput.Grams(200), CalorieInput.parse("200 grams"))
        assertEquals(CalorieInput.Grams(250), CalorieInput.parse("0.25kg"))
    }
    @Test fun perAmountTimesEaten() {
        assertEquals(CalorieInput.PerAmount(kcal = 150, perGrams = 100, ateGrams = 200, total = 300), CalorieInput.parse("150x100x200"))
        assertEquals(300, (CalorieInput.parse("150 × 100 × 200") as CalorieInput.PerAmount).total)
        assertEquals(300, (CalorieInput.parse("150kcal x 100g x 200g") as CalorieInput.PerAmount).total)
        assertEquals(75, (CalorieInput.parse("150*100*50") as CalorieInput.PerAmount).total)
        assertEquals(53, (CalorieInput.parse("80x30x20") as CalorieInput.PerAmount).total) // 53.3 rounds
    }
    @Test fun emptyAndNonsense() {
        assertEquals(CalorieInput.Empty, CalorieInput.parse(""))
        assertEquals(CalorieInput.Invalid, CalorieInput.parse("lots"))
        assertEquals(CalorieInput.Invalid, CalorieInput.parse("0x100x200"))
        assertEquals(CalorieInput.Invalid, CalorieInput.parse("99999")) // above the 20 000 sanity cap
    }
    @Test fun trailingTokenSplitsFromText() {
        assertEquals("oatmeal" to CalorieInput.Kcal(150), CalorieInput.splitTrailing("oatmeal 150 kcal"))
        assertEquals("rice" to CalorieInput.Grams(200), CalorieInput.splitTrailing("rice 200g"))
        assertEquals("chips" to CalorieInput.PerAmount(150, 100, 200, 300), CalorieInput.splitTrailing("chips 150x100x200"))
        // A bare number is part of the food ("2 eggs", "coffee 2"), never calories.
        assertEquals("2 eggs" to CalorieInput.Empty, CalorieInput.splitTrailing("2 eggs"))
        assertEquals("coffee 2" to CalorieInput.Empty, CalorieInput.splitTrailing("coffee 2"))
        // Only the end counts; "200g" alone is a food text, not an empty food.
        assertEquals("200g" to CalorieInput.Empty, CalorieInput.splitTrailing("200g"))
        // Units on the per-amount form must not let the last "200g" split off on its own.
        assertEquals(300, (CalorieInput.splitTrailing("chips 150kcal x 100g x 200g").second as CalorieInput.PerAmount).total)
        assertEquals("rice" to CalorieInput.Grams(200), CalorieInput.splitTrailing("rice 200 grams"))
    }
}
