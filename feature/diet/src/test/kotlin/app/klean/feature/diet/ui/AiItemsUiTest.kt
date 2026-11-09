package app.klean.feature.diet.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.FakePhotos
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.entry.FoodEntryViewModel
import app.klean.feature.diet.ui.today.DietRow
import app.klean.shared.diet.FoodItemState
import app.klean.shared.time.atMinute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiItemsUiTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 15 * 60, Dubai))
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val fmt = AndroidDietFormatter(ctx, DubaiZone)
    private val repo = FakeDietRepository(clock).apply {
        entries.value = listOf(entry("e1", "shawarma and fries", atMinute(nov9, 13 * 60, Dubai)), entry("e2", "tea", atMinute(nov9, 14 * 60, Dubai)))
        items.value = listOf(
            item("i1", "e1", 0, "shawarma", "1 wrap", 450),
            item("i2", "e1", 1, "fries", "medium", 1400),
            item("i3", "e1", 2, "cola", null, 140).copy(state = FoodItemState.REJECTED),
        )
    }
    private val builder = DietRowBuilder(repo, fmt, FakePhotos())

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun rows(showCalories: Boolean) =
        builder.build(nov9, repo.entries.value, repo.items.value, repo.prefs.copy(showCalories = showCalories), clock.nowMs)
            .filterIsInstance<DietRow.Entry>()

    @Test fun rowsListItemsWithPortionAndProviderButNoCaloriesByDefault() {
        val r = rows(showCalories = false).first { it.id == "e1" }
        assertEquals("shawarma and fries", r.text)
        assertEquals(listOf("shawarma · 1 wrap", "fries · medium"), r.itemLines)
        assertEquals("openai_compat", r.providerId)
        val tea = rows(showCalories = false).first { it.id == "e2" }
        assertEquals(emptyList<String>(), tea.itemLines)
        assertNull(tea.providerId)
    }

    @Test fun caloriesAppearOnlyWhenShowCaloriesIsOn() {
        val r = rows(showCalories = true).first { it.id == "e1" }
        assertEquals(listOf("shawarma · 1 wrap · ~450 kcal", "fries · medium · ~1,400 kcal"), r.itemLines)
    }

    @Test fun dayTotalIsOnlyFormattedWhenEnabled() {
        assertNull(caloriesSoFar(repo.items.value, showCalories = false, fmt))
        assertEquals("~1,850 kcal so far", caloriesSoFar(repo.items.value, showCalories = true, fmt))
        assertNull(caloriesSoFar(emptyList(), showCalories = true, fmt))
    }

    @Test fun providerLabelsAreHumanNames() {
        assertEquals(R.string.diet_provider_gemini, providerLabelRes("gemini"))
        assertEquals(R.string.diet_provider_local, providerLabelRes("openai_compat"))
        assertEquals(R.string.diet_provider_on_device, providerLabelRes("on_device"))
        assertEquals("Local server", ctx.getString(providerLabelRes("openai_compat")!!))
        assertNull(providerLabelRes("user"))
    }

    private fun entryVm() = FoodEntryViewModel(SavedStateHandle(mapOf("entryId" to "e1")), repo, DubaiZone, fmt, FakePhotos())

    @Test fun entryScreenShowsItemsAndActionsUpdateState() = runTest {
        val vm = entryVm()
        val s = vm.uiState.first { it.loaded && it.items.isNotEmpty() }
        assertEquals(listOf("i1", "i2"), s.items.map { it.id })
        assertEquals("shawarma · 1 wrap", s.items[0].line)
        assertEquals("openai_compat", s.providerId)

        vm.editItem("i1", "chicken shawarma", "1 large wrap", 520)
        val edited = repo.items.value.first { it.id == "i1" }
        assertEquals(FoodItemState.EDITED, edited.state)
        assertEquals("openai_compat", edited.providerId)
        assertEquals("chicken shawarma · 1 large wrap", vm.uiState.value.items[0].line)

        vm.rejectItem("i2")
        assertEquals(listOf("i1"), vm.uiState.value.items.map { it.id })

        vm.removeAiDetails()
        assertEquals(emptyList<String>(), vm.uiState.value.items.map { it.id })
        assertNull(vm.uiState.value.providerId)
        assertEquals("raw text is never touched", "shawarma and fries", repo.entries.value.first { it.id == "e1" }.rawText)
    }
}
