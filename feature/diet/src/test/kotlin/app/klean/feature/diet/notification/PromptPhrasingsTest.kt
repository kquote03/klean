package app.klean.feature.diet.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.R
import app.klean.shared.diet.prompt.MealLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PromptPhrasingsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val all = PromptPhrasings.generic + MealLabel.entries.flatMap(PromptPhrasings::sinceMeal)

    @Test fun eightGenericAndTwoPerMeal() {
        assertEquals(8, PromptPhrasings.generic.size)
        MealLabel.entries.forEach { assertEquals(it.name, 2, PromptPhrasings.sinceMeal(it).size) }
    }

    @Test fun idsAreUniqueAndFindable() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertEquals(it, PromptPhrasings.byId(it.id)) }
        assertNull(PromptPhrasings.byId("nope"))
    }

    @Test fun copyIsExactlyAsPlanned() {
        assertEquals("What did you eat?", ctx.getString(PromptPhrasings.byId("g1")!!.res))
        assertEquals("Had something? Add it here.", ctx.getString(PromptPhrasings.byId("g8")!!.res))
        assertEquals("Lunch and after: anything to add?", ctx.getString(PromptPhrasings.byId("m_l2")!!.res))
        assertEquals("A late snack to note?", ctx.getString(PromptPhrasings.byId("m_n2")!!.res))
        assertEquals("What did you order?", ctx.getString(R.string.prompt_delivery))
        assertEquals("Anything from Home?", ctx.getString(R.string.prompt_geofence, "Home"))
    }

    @Test fun calmCopyOnly() {
        val guilt = Regex("(?i)missed|streak|behind|forgot|failed|!")
        all.map { ctx.getString(it.res) }.forEach { assertFalse(it, guilt.containsMatchIn(it)); assertTrue(it.isNotBlank()) }
    }
}
