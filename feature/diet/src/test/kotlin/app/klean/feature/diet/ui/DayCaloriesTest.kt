package app.klean.feature.diet.ui

import app.klean.core.data.db.entity.FoodItemEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.shared.diet.FoodItemState
import app.klean.shared.diet.KcalSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DayCaloriesTest {
    private val repo = FakeDietRepository(FakeClock(0))
    private fun e(id: String, kcal: Int? = null, source: KcalSource? = null) =
        repo.entry(id, id, 1L).copy(kcal = kcal, kcalSource = source?.name)
    private fun item(entry: String, kcal: Int?, state: FoodItemState = FoodItemState.ACCEPTED) = FoodItemEntity(
        id = "i-$entry-$kcal", entryId = entry, position = 0, name = "x", portion = null, calories = kcal,
        providerId = "gemini", model = null, state = state, sync = SyncColumns(1, 1, "dev", false, 1),
    )

    @Test fun mixOfOwnFilledAndNone() {
        val entries = listOf(e("own", 300, KcalSource.USER), e("hist", 105, KcalSource.HISTORY), e("ai", 260, KcalSource.AI), e("old"), e("none"))
        val items = listOf(item("old", 200), item("old", 50), item("old", 999, FoodItemState.REJECTED), item("own", 999))
        assertEquals(DayCalories(total = 915, estimated = true, missing = 1), dayCalories(entries, items))
    }

    @Test fun onlyOwnNumbersAreExact() {
        assertFalse(dayCalories(listOf(e("a", 300, KcalSource.USER)), emptyList()).estimated)
        assertEquals(DayCalories(0, false, 0), dayCalories(emptyList(), emptyList()))
    }

    @Test fun labels() {
        val fmt = object : DietFormatter {
            override fun time(epochMs: Long) = ""
            override fun date(date: kotlinx.datetime.LocalDate) = ""
            override fun gap(gap: app.klean.shared.diet.Gap) = ""
        }
        assertEquals("300 kcal", fmt.kcalWithSource(entryKcal(e("a", 300, KcalSource.USER), emptyList())!!))
        assertEquals("≈ 105 kcal · from earlier", fmt.kcalWithSource(entryKcal(e("a", 105, KcalSource.HISTORY), emptyList())!!))
        assertEquals("≈ 260 kcal · estimate", fmt.kcalWithSource(entryKcal(e("a", 260, KcalSource.AI), emptyList())!!))
        assertEquals("≈ 250 kcal", fmt.kcalLabel(entryKcal(e("old"), listOf(item("old", 250)))!!))
        assertNull(entryKcal(e("none"), emptyList()))
        assertEquals("≈ 555 kcal so far", fmt.daySoFar(DayCalories(555, true, 1), showCalories = true))
        assertNull(fmt.daySoFar(DayCalories(555, true, 1), showCalories = false))
        assertEquals("1 food without calories", fmt.withoutCalories(1))
    }
}
