package app.klean.core.capture.voice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.capture.DubaiZone
import app.klean.core.capture.FakeHandler
import app.klean.core.capture.FakeModuleGate
import app.klean.core.capture.R
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.capture.captureRouter
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plan 2026-11-11 V3: the dedicated voice screen. Live transcript while listening; then the text, where it goes
 * (the router's pick selected) and Save; a missing language pack offers Download. Light and dark references.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VoiceCaptureScreenTest {
    @get:Rule val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val diet = FakeHandler(ModuleId.DIET)
    private val tasks = FakeHandler(ModuleId.TASKS)
    private val reminders = FakeHandler(ModuleId.REMINDERS)
    private val calendar = FakeHandler(ModuleId.CALENDAR)
    private val workout = FakeHandler(ModuleId.WORKOUT)
    private val gate = FakeModuleGate()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-11T09:00:00Z") }
    private val rec = FakeVoiceRecognizer()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg extras: Pair<String, Any?>) = VoiceCaptureViewModel(
        rec,
        UniversalCaptureService(setOf(diet, tasks, reminders, calendar, workout), gate, captureRouter(null, clock, DubaiZone)),
        captureRouter(null, clock, DubaiZone),
        ModuleRegistry(emptySet(), gate),
        clock,
        SavedStateHandle(mapOf(*extras)),
    )

    private fun render(
        name: String?,
        voice: VoiceUiState = VoiceUiState(),
        review: VoiceReviewState = VoiceReviewState(),
        onSave: (String, ModuleId) -> Unit = { _, _ -> },
        onDownload: () -> Unit = {},
    ) {
        val content = @androidx.compose.runtime.Composable {
            VoiceCaptureContent(
                voice = voice, review = review,
                onClose = {}, onDone = {}, onMic = {}, onSpeakAgain = {}, onTextChange = {}, onSelect = {},
                onSave = onSave, onUndo = {}, onDownload = onDownload, onUseSystemVoice = {},
            )
        }
        if (name != null) compose.shot(name, screen = true) { content() } else compose.setContent { content() }
    }

    @Test fun listeningShowsThePartialText() {
        render("voice-listening", voice = VoiceUiState(listening = true, partial = "a banana and", level = 0.6f))
        compose.onNodeWithText("a banana and").assertIsDisplayed()
        compose.onNodeWithText("Listening").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.assertClutterBudget(maxInteractive = 7, maxTextInputs = 1)
    }

    @Test fun finalShowsWhereItGoesWithFoodSelected() {
        lateinit var review: VoiceReviewState
        runTest(dispatcher) {
            val vm = vm()
            vm.review("a banana and coffee")
            runCurrent()
            review = vm.state.value
        }
        assertEquals(ModuleId.DIET, review.selected)
        assertTrue(review.destinations.size <= VoiceCaptureViewModel.MAX_DESTINATIONS)
        val saved = mutableListOf<Pair<String, ModuleId>>()
        render("voice-review", review = review, onSave = { t, m -> saved += t to m })
        compose.onNodeWithText("a banana and coffee").assertIsDisplayed()
        compose.onNodeWithText("Food").assertIsSelected()
        compose.onNodeWithText("Task").assertIsNotSelected()
        compose.assertClutterBudget(maxInteractive = 7, maxTextInputs = 1)
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("a banana and coffee" to ModuleId.DIET), saved)
    }

    @Test fun needsPackOffersDownload() {
        var downloads = 0
        val voice = VoiceUiState(
            messageRes = R.string.voice_needs_pack,
            messageArg = "English",
            error = VoiceError.NeedsLanguagePack("en-US", canDownload = true),
        )
        render("voice-needs-pack", voice = voice, onDownload = { downloads++ })
        compose.onNodeWithText("Voice needs the English pack to work offline").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        assertEquals(1, downloads)
        compose.onNodeWithText("Use Google voice").assertDoesNotExist()
    }

    @Test fun noServiceIsSaidPlainlyAndHidesGoogleVoice() {
        render(null, voice = VoiceUiState(messageRes = R.string.voice_no_service, error = VoiceError.NoService))
        compose.onNodeWithText("This phone has no speech recognition").assertIsDisplayed()
        compose.onNodeWithText("Use Google voice").assertDoesNotExist()
        compose.onNodeWithText("offline", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test fun networkOffersGoogleVoiceWhenItResolves() {
        render(null, voice = VoiceUiState(messageRes = R.string.voice_network, error = VoiceError.Network, systemDialogAvailable = true))
        compose.onNodeWithText("Use Google voice").assertIsDisplayed()
        compose.onNodeWithContentDescription("Try again").assertIsDisplayed()
    }

    @Test fun savedOffersUndo() {
        render("voice-saved", review = VoiceReviewState(reviewing = true, text = "tea", saved = VoiceDestination(ModuleId.DIET, R.string.voice_dest_food)))
        compose.onNodeWithText("Saved to Food").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test fun heardTextGoesToReviewAndSavesViaVoiceThenCloses() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        vm.voice.onMic()
        rec.final("tea")
        runCurrent()
        assertTrue(vm.state.value.reviewing)
        assertEquals("tea", vm.state.value.text)
        assertEquals(ModuleId.DIET, vm.state.value.selected)
        vm.save("tea", ModuleId.TASKS)
        runCurrent()
        val input = tasks.executed.single()
        assertTrue(input.viaVoice)
        assertEquals(ModuleId.TASKS, input.targetModule)
        assertEquals(ModuleId.TASKS, vm.state.value.saved?.module)
        assertFalse(vm.state.value.finished)
        advanceTimeBy(VoiceCaptureViewModel.SAVED_MS + 1)
        assertTrue(vm.state.value.finished)
    }

    @Test fun keepingThePredictionRoutesLikeTypedCapture() = runTest(dispatcher) {
        val vm = vm()
        vm.review("a banana and coffee")
        runCurrent()
        vm.save("a banana and coffee", ModuleId.DIET)
        runCurrent()
        assertNull(diet.executed.single().targetModule)
    }

    @Test fun aimedAtAModuleSelectsItAndUndoReturnsToReview() = runTest(dispatcher) {
        val vm = vm(VoiceRoute.ARG_MODULE to "workout")
        vm.review("a banana and coffee")
        runCurrent()
        assertEquals(ModuleId.WORKOUT, vm.state.value.selected)
        assertEquals(ModuleId.WORKOUT, vm.state.value.destinations.first().module)
        vm.save("a banana and coffee", ModuleId.WORKOUT)
        runCurrent()
        vm.undo()
        runCurrent()
        assertEquals(listOf("workout-1"), workout.undone)
        assertNull(vm.state.value.saved)
        assertTrue(vm.state.value.reviewing)
        advanceTimeBy(VoiceCaptureViewModel.SAVED_MS + 1)
        assertFalse(vm.state.value.finished)
    }
}
