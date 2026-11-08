package app.klean.core.capture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.voice.FakeVoiceRecognizer
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CapturePhotoTest {
    private val dispatcher = StandardTestDispatcher()
    private val diet = FakeHandler(ModuleId.DIET)
    private val gate = FakeModuleGate()
    private val photos = FakePhotoStorage()
    private val clock = object : Clock { override fun now() = Instant.fromEpochMilliseconds(42) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CaptureViewModel(
        UniversalCaptureService(setOf(diet), gate, DefaultCaptureRouter(null)),
        ModuleRegistry(emptySet(), gate),
        clock,
        FakeVoiceRecognizer(),
        photos,
        DubaiZone,
        SavedStateHandle(),
    )

    @Test fun cameraPhotoIsImportedTheOriginalDeletedAndAttachedToTheSave() = runTest(dispatcher) {
        val vm = vm()
        val (name, uri) = vm.newCaptureUri()
        vm.onPhotoTaken(name, uri, success = true)
        runCurrent()
        assertEquals(listOf(uri), photos.imported)
        assertEquals(listOf(name), photos.deleted)
        assertEquals("imported-1.jpg", vm.state.value.photoFile)
        vm.submit("rice and dal")
        runCurrent()
        assertEquals("imported-1.jpg", diet.executed.single().photoFile)
    }

    @Test fun cancelledCameraDropsTheEmptyFile() = runTest(dispatcher) {
        val vm = vm()
        val (name, uri) = vm.newCaptureUri()
        vm.onPhotoTaken(name, uri, success = false)
        runCurrent()
        assertTrue(photos.imported.isEmpty())
        assertEquals(listOf(name), photos.deleted)
        assertNull(vm.state.value.photoFile)
    }

    @Test fun galleryPhotoAttachesAndCanBeRemoved() = runTest(dispatcher) {
        val vm = vm()
        vm.onPhotoPicked(Uri.parse("content://media/1"))
        runCurrent()
        assertEquals("imported-1.jpg", vm.state.value.photoFile)
        vm.removePhoto()
        assertNull(vm.state.value.photoFile)
        assertEquals(listOf("imported-1.jpg"), photos.deleted)
        vm.submit("tea")
        runCurrent()
        assertNull(diet.executed.single().photoFile)
    }

    @Test fun savingWithoutAPhotoStillWorks() = runTest(dispatcher) {
        val vm = vm()
        vm.submit("tea")
        runCurrent()
        assertNull(diet.executed.single().photoFile)
    }
}
