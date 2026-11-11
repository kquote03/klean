package app.klean.feature.diet.ui.entry

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.FakePhotos
import app.klean.feature.diet.ui.AndroidDietFormatter
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class FoodEntryViewModelTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 15 * 60, Dubai))
    private val repo = FakeDietRepository(clock).apply {
        entries.value = listOf(entry("e1", "toast", atMinute(nov9, 12 * 60, Dubai)))
    }
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val fmt = AndroidDietFormatter(ctx, DubaiZone)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val photos = FakePhotos()

    private fun vm() = FoodEntryViewModel(SavedStateHandle(mapOf("entryId" to "e1")), repo, DubaiZone, fmt, photos)

    @Test fun cameraPhotoImportsDropsTheOriginalAndAttaches() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        val (name, uri) = vm.newCaptureUri()
        vm.onPhotoTaken(name, uri, success = true)
        assertEquals(listOf(uri), photos.imported)
        assertEquals(listOf(name), photos.deleted)
        assertEquals("imported-1.jpg", repo.edits.single().photoFile)
        assertEquals("/photos/imported-1.jpg", vm.uiState.value.photo?.path)
    }

    @Test fun cancelledCameraChangesNothing() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        val (name, uri) = vm.newCaptureUri()
        vm.onPhotoTaken(name, uri, success = false)
        assertTrue(photos.imported.isEmpty())
        assertEquals(listOf(name), photos.deleted)
        assertTrue(repo.edits.isEmpty())
    }

    @Test fun caloriesShowWhereTheyCameFrom() = runTest {
        repo.prefs = repo.prefs.copy(showCalories = true)
        repo.entries.value = repo.entries.value.map { it.copy(kcal = 300, kcalSource = "USER") }
        assertEquals("300 kcal", vm().uiState.first { it.kcalLabel != null }.kcalLabel)
        repo.entries.value = repo.entries.value.map { it.copy(kcal = 105, kcalSource = "HISTORY") }
        assertEquals("≈ 105 kcal · from earlier", vm().uiState.first { it.kcalLabel != null }.kcalLabel)
        repo.entries.value = repo.entries.value.map { it.copy(kcal = 260, kcalSource = "AI") }
        val vm = vm()
        assertEquals("≈ 260 kcal · estimate", vm.uiState.first { it.kcalLabel != null }.kcalLabel)
        assertEquals("", vm.uiState.value.calories) // a filled-in number isn't put in the slot
    }

    @Test fun theSlotSavesThroughTheRepositoryAndEmptyClears() = runTest {
        repo.prefs = repo.prefs.copy(showCalories = true)
        val vm = vm()
        vm.uiState.first { it.loaded }
        vm.onCaloriesChange("150x100x200")
        vm.saveCalories()
        assertEquals(listOf("e1" to "150x100x200"), repo.calorieSets)
        assertEquals("300 kcal", vm.uiState.first { it.kcalLabel == "300 kcal" }.kcalLabel)
        assertEquals("300", vm.uiState.value.calories)

        vm.onCaloriesChange("")
        vm.save()
        assertEquals("e1" to "", repo.calorieSets.last())
        vm.save() // nothing changed since
        assertEquals(2, repo.calorieSets.size)
    }

    @Test fun anUnreadableSlotIsNotSaved() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        vm.onCaloriesChange("lots")
        vm.save()
        assertTrue(repo.calorieSets.isEmpty())
    }

    @Test fun loadsEntry() = runTest {
        val s = vm().uiState.first { it.loaded }
        assertEquals("toast", s.text)
        assertEquals(fmt.time(atMinute(nov9, 12 * 60, Dubai)), s.timeLabel)
    }

    @Test fun changingTimeEditsEatenAtOnly() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        vm.onTimePicked(9, 15)
        assertEquals(
            listOf(FakeDietRepository.EditCall("e1", null, atMinute(nov9, 9 * 60 + 15, Dubai), null, false)),
            repo.edits,
        )
    }

    @Test fun changingDateKeepsTimeOfDay() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        vm.onDatePicked(LocalDate(2026, 11, 7))
        assertEquals(atMinute(LocalDate(2026, 11, 7), 12 * 60, Dubai), repo.edits.single().eatenAt)
    }

    @Test fun saveTrimsTextAndSkipsWhenUnchanged() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        vm.save()
        assertTrue(repo.edits.isEmpty())
        vm.onTextChange("  toast with jam ")
        vm.save()
        assertEquals("toast with jam", repo.edits.single().text)
    }

    /** Found on device: after Delete the screen stayed on the greyed-out entry. It should close. */
    @Test fun deleteTombstonesAndAsksToClose() = runTest {
        val vm = vm()
        vm.uiState.first { it.loaded }
        assertEquals(null, vm.uiState.value.removed)
        vm.delete()
        assertEquals(listOf("e1"), repo.deletes)
        assertEquals("e1", vm.uiState.value.removed)
        vm.save() // leaving after delete must not write the text back
        assertTrue(repo.edits.isEmpty())
    }
}
