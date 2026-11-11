package app.klean.feature.diet.calories

import app.klean.shared.diet.KcalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCaloriesTest {
    private fun p(text: String, kcal: Int, at: Long, grams: Int? = null, source: KcalSource = KcalSource.USER) =
        PastEntry(text, kcal, grams, source, at)

    @Test fun sameFoodNoGramsTakesTheNewest() {
        assertEquals(110, historyKcal("banana", null, listOf(p("banana", 105, 1), p("banana", 110, 2))))
    }

    @Test fun namesAreNormalised() {
        assertEquals(300, historyKcal("McDonald's  Cheeseburger.", null, listOf(p("mcdonalds cheeseburger", 300, 1))))
        assertEquals("mcdonalds cheeseburger", normaliseFood("McDonald\u2019s  Cheeseburger!"))
    }

    @Test fun gramsOnBothSidesScalePerGram() {
        assertEquals(260, historyKcal("rice", 200, listOf(p("rice", 195, 1, grams = 150))))
    }

    @Test fun gramsNowButNoneBeforeGoesToTheAi() {
        assertNull(historyKcal("rice", 200, listOf(p("rice", 195, 1))))
    }

    @Test fun historySourcedRowsAreIgnored() {
        assertEquals(105, historyKcal("banana", null, listOf(p("banana", 105, 1), p("banana", 999, 2, source = KcalSource.HISTORY))))
        assertNull(historyKcal("banana", null, listOf(p("banana", 999, 2, source = KcalSource.HISTORY))))
    }

    @Test fun noMatch() {
        assertNull(historyKcal("salad", null, listOf(p("banana", 105, 1))))
        assertNull(historyKcal("salad", null, emptyList()))
    }
}
