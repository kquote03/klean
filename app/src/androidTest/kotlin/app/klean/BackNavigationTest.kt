package app.klean

import android.Manifest
import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.isDisplayed
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import app.klean.core.ui.module.ModuleRegistry
import app.klean.feature.calendar.data.CalendarRepository
import app.klean.feature.calendar.data.EventDraft
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.ContactRepository
import app.klean.feature.diet.data.DietRepository
import app.klean.feature.onboarding.OnboardingState
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TasksRepository
import app.klean.nav.NewModulesStore
import app.klean.shared.diet.CaptureSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * The Back audit (plan 2026-11-11, E1): every screen and overlay, then Back, lands on the screen it came from and never
 * closes the app, except Back on Today. Back is a real key event, so dialogs and sheets get it as they would on a phone. Each path is its own test so a broken one names itself. Waydroid only:
 * `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.klean.BackNavigationTest`.
 */
@HiltAndroidTest
class BackNavigationTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var onboarding: OnboardingState
    @Inject lateinit var newModules: NewModulesStore
    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var tasks: TasksRepository
    @Inject lateinit var calendar: CalendarRepository
    @Inject lateinit var diet: DietRepository
    @Inject lateinit var contacts: ContactRepository
    @Inject lateinit var clock: Clock

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setUp() = runBlocking {
        hilt.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        onboarding.markDone()
        newModules.clear()
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await(TODAY)
    }

    // --- Screens, by what only they show -------------------------------------------------------------------------

    /** Today: the avatar that opens the account menu. */
    private val TODAY = hasContentDescription("Account and settings")
    private val SETTINGS_HUB = isHeading() and hasText("Settings")
    private val BACK_ARROW = hasContentDescription("Back")
    private val ADD = hasContentDescription("Add")
    private val ADD_OPEN = hasContentDescription("Close the Add menu")
    private val MORE = hasContentDescription("More options")
    private val TEXT_FIELD = hasSetTextAction()

    /**
     * On screen, not merely composed: the pager keeps each neighbour page composed off screen (Today beside Plan), so a
     * node that exists may sit outside the viewport.
     */
    private fun exists(m: SemanticsMatcher) = shownIndex(m) >= 0

    private fun shownIndex(m: SemanticsMatcher): Int {
        val nodes = compose.onAllNodes(m)
        return (0 until nodes.fetchSemanticsNodes().size).firstOrNull { nodes[it].isDisplayed() } ?: -1
    }

    private fun await(m: SemanticsMatcher, why: String = m.description) {
        try {
            compose.waitUntil(TIMEOUT) { exists(m) }
        } catch (e: Throwable) {
            throw AssertionError("expected on screen: $why\n${screen()}", e)
        }
    }

    private fun awaitGone(m: SemanticsMatcher, why: String = m.description) {
        try {
            compose.waitUntil(TIMEOUT) { !exists(m) }
        } catch (e: Throwable) {
            throw AssertionError("expected gone: $why\n${screen()}", e)
        }
    }

    /** What is on screen, for a failure message: every window's semantics tree, shortened. */
    private fun screen(): String = runCatching {
        val roots = compose.onAllNodes(isRoot())
        (0 until roots.fetchSemanticsNodes().size).joinToString("\n---\n") { roots[it].printToString(maxDepth = 12) }.take(4_000)
    }.getOrElse { "(no tree: ${it.message})" }

    private fun tap(m: SemanticsMatcher) {
        await(m)
        compose.onAllNodes(m)[shownIndex(m)].performClick()
        compose.waitForIdle()
    }

    private fun tapText(text: String) = tap(hasText(text) and hasClickAction())

    /** A real Back key to the focused window (a sheet's or a menu's own window when one is open). */
    private fun back() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    private fun assertStillOpen() = scenario.onActivity { assertFalse("the app closed", it.isFinishing) }

    private fun bar(label: String) = tap(hasText(label) and hasClickAction())

    private fun openSettings() {
        tap(TODAY)
        tapText("Settings")
        await(SETTINGS_HUB)
    }

    // --- 1, 2: the account menu ----------------------------------------------------------------------------------

    @Test fun accountMenuBackIsToday() {
        launch()
        tap(TODAY)
        await(hasText("More from Klean"))
        back()
        await(TODAY)
        assertStillOpen()
    }

    @Test fun settingsFromTheAccountMenuBackIsToday() {
        launch()
        openSettings()
        back()
        await(TODAY)
        awaitGone(hasText("More from Klean"), "the account menu (Settings replaced it)")
        assertStillOpen()
    }

    // --- 3: every Settings sub-screen ------------------------------------------------------------------------------

    @Test fun everySettingsScreenBackIsTheHub() {
        val modules = runBlocking { registry.enabled.first() }.filter { it.settingsRoute != null }.map { ctx.getString(it.labelRes) }
        val rows = modules + listOf(
            "Turn modules on or off", "Quiet hours", "Appearance", "AI providers", "Backup", "Sync", "Permissions", "About",
        )
        launch()
        openSettings()
        val broken = mutableListOf<String>()
        for (row in rows) {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(row) and hasClickAction())
            tapText(row)
            try {
                awaitGone(SETTINGS_HUB, "the hub after opening $row")
                back()
                await(SETTINGS_HUB, "the hub after Back from $row")
                assertStillOpen()
            } catch (e: Throwable) {
                broken += "$row: ${e.message}"
                if (!exists(SETTINGS_HUB)) {
                    // Recover for the next row: start again from Today.
                    scenario.close()
                    launch()
                    openSettings()
                }
            }
        }
        assertEquals("Settings screens whose Back is wrong", emptyList<String>(), broken)
    }


    // --- 4, 5, 6: Plan ----------------------------------------------------------------------------------------------

    @Test fun taskDetailBackIsTasks() {
        runBlocking { tasks.add(TaskDraft("Back audit task", dueDate = tasks.today())) }
        launch()
        bar("Plan")
        // Plan opens where it was left last (it may be Calendar, whose task rows don't open the task).
        tapText("Tasks")
        tap(hasText("Back audit task", substring = true) and hasClickAction())
        await(BACK_ARROW, "the task's page")
        back()
        awaitGone(BACK_ARROW, "the task's page")
        await(hasText("Back audit task", substring = true))
        assertStillOpen()
    }

    @Test fun eventBackIsCalendar() {
        val now = clock.now().toEpochMilliseconds()
        runBlocking {
            calendar.add(EventDraft("Back audit event", startAt = now + HOUR, endAt = now + 2 * HOUR, zone = TimeZone.currentSystemDefault().id))
        }
        launch()
        bar("Plan")
        tapText("Calendar")
        tap(hasText("Back audit event", substring = true) and hasClickAction())
        await(TEXT_FIELD, "the event editor")
        back()
        awaitGone(TEXT_FIELD, "the event editor")
        await(hasText("Back audit event", substring = true))
        assertStillOpen()
    }

    /** Calendar views are a choice inside one screen (like tabs), not pages: Back leaves Plan for Today. */
    @Test fun calendarWeekViewBackLeavesPlan() {
        launch()
        bar("Plan")
        tapText("Calendar")
        tap(MORE)
        tapText("Week")
        awaitGone(hasText("Agenda"), "the view menu")
        back()
        await(TODAY)
        assertStillOpen()
    }

    // --- 7, 8: Track ------------------------------------------------------------------------------------------------

    @Test fun dietHistoryDayEntryBackInOrder() {
        runBlocking { diet.logEntry("Back audit toast", CaptureSource.DASHBOARD) }
        launch()
        bar("Track")
        tapText("Diet")
        tap(MORE)
        tapText("History")
        await(hasText("History"))
        tap(hasContentDescription("1 entry", substring = true) and hasClickAction())
        awaitGone(hasText("History"), "History (a day opened)")
        tap(hasText("Back audit toast", substring = true) and hasClickAction())
        await(hasText("Entry"))
        back()
        awaitGone(hasText("Entry"), "the entry (Back → the day)")
        await(hasText("Back audit toast", substring = true))
        assertFalse("Back from the entry skipped the day", exists(hasText("History")))
        back()
        await(hasText("History"), "History (Back from the day)")
        back()
        awaitGone(hasText("History"), "History (Back → Diet)")
        await(MORE)
        await(hasText("Back audit toast", substring = true))
        assertStillOpen()
    }

    /** Workout has no start sheet (its start choices are cards on the screen): a session's Back returns to Workout. */
    @Test fun workoutSessionBackIsWorkout() {
        launch()
        bar("Track")
        tapText("Workout")
        // An empty Workout offers \"Start a workout\"; with anything logged, an \"Empty workout\" card.
        tap((hasText("Start a workout") or hasText("Empty workout", substring = true)) and hasClickAction())
        await(hasText("Finish"), "the session")
        back()
        awaitGone(hasText("Finish"), "the session")
        await(MORE, "Workout")
        assertStillOpen()
    }

    // --- 9: People --------------------------------------------------------------------------------------------------

    @Test fun contactEditBackIsTheContact() {
        runBlocking { contacts.add(ContactDraft("Back Audit Person")) }
        launch()
        bar("People")
        tap(hasText("Back Audit Person") and hasClickAction())
        tap(hasContentDescription("Edit contact"))
        await(TEXT_FIELD, "the contact editor")
        back()
        awaitGone(TEXT_FIELD, "the contact editor")
        await(hasContentDescription("Edit contact"), "the contact's page")
        back()
        await(hasText("Back Audit Person"))
        awaitGone(hasContentDescription("Edit contact"), "the contact's page")
        assertStillOpen()
    }

    // --- 10, 11, 12: overlays ---------------------------------------------------------------------------------------

    @Test fun addMenuBackClosesOnlyTheMenu() {
        launch()
        bar("Plan")
        tap(ADD)
        await(ADD_OPEN)
        back()
        await(ADD)
        awaitGone(ADD_OPEN)
        assertFalse("Back left Plan", exists(TODAY))
        assertStillOpen()
    }

    @Test fun overflowMenuBackClosesOnlyTheMenu() {
        launch()
        bar("Plan")
        tap(MORE)
        await(hasText("Settings") and hasClickAction())
        back()
        awaitGone(hasText("Settings") and hasClickAction(), "the ⋮ menu")
        assertFalse("Back left Plan", exists(TODAY))
        await(MORE)
        assertStillOpen()
    }

    @Test fun captureSheetBackClosesOnlyTheSheet() {
        launch()
        bar("Plan")
        tap(ADD)
        tapText("Task")
        await(TEXT_FIELD, "the capture sheet")
        // The sheet opens with the keyboard up, and the first Back puts the keyboard away (as everywhere on Android);
        // without a keyboard (no IME on the device) the first Back closes the sheet.
        back()
        Thread.sleep(SETTLE_MS)
        if (exists(TEXT_FIELD)) back()
        awaitGone(TEXT_FIELD, "the capture sheet")
        assertFalse("Back left Plan", exists(TODAY))
        assertStillOpen()
    }

    // --- 13, 14, 15 -------------------------------------------------------------------------------------------------

    /** The pager: a swipe from Today shows Plan; Back slides back to Today, and Back there leaves the app. */
    @Test fun swipeToPlanBackIsToday() {
        launch()
        compose.onAllNodes(isRoot()).onFirst().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        awaitGone(TODAY, "Today (swiped to Plan)")
        back()
        await(TODAY)
        assertStillOpen()
    }

    @Test fun planBackIsTodayAndTodayBackLeaves() {
        launch()
        bar("Plan")
        awaitGone(TODAY)
        back()
        await(TODAY)
        assertStillOpen()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        // Android 12+ moves a launcher task to the back instead of finishing it; either way the app has left.
        compose.waitUntil(TIMEOUT) { !scenario.state.isAtLeast(Lifecycle.State.STARTED) }
    }

    /** Seen on device: Today in the bar did nothing from another page. */
    @Test fun todayInTheBarFromTrackIsToday() {
        launch()
        bar("Plan")
        awaitGone(TODAY)
        bar("Track")
        awaitGone(TODAY)
        tap(hasText("Today") and hasClickAction() and isSelectable() and SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        await(TODAY, "Today after its bar item")
        assertStillOpen()
    }

    @Test fun arrangeTodayBackIsToday() {
        launch()
        tap(MORE)
        tapText("Arrange Today")
        awaitGone(TODAY)
        back()
        await(TODAY)
        assertStillOpen()
    }

    @Test fun setupAgainBackGoesAPageThenToSettings() {
        launch()
        openSettings()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Run setup again"))
        tap(hasText("Run setup again", substring = true) and hasClickAction())
        await(hasText("1 of", substring = true), "the wizard's first page")
        tapText("Set up")
        await(hasText("2 of", substring = true), "the wizard's second page")
        back()
        await(hasText("1 of", substring = true), "the first page after Back")
        back()
        await(SETTINGS_HUB, "Settings after Back from the first page")
        assertStillOpen()
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val HOUR = 3_600_000L
        const val SETTLE_MS = 1_000L
    }
}
