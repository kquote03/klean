package app.klean.feature.sleep.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sleep.SleepScheduleRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.sleep.Dubai
import app.klean.feature.sleep.DubaiZone
import app.klean.feature.sleep.FakeClock
import app.klean.feature.sleep.data.DefaultSleepRepository
import app.klean.feature.sleep.inMemoryDb
import app.klean.feature.sleep.tempDevicePrefs
import app.klean.shared.time.atMinute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class SleepScreenTest {
    @get:Rule val rule = createComposeRule()
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 9), 23 * 60 + 40, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val schedule = SleepScheduleRepository(db, stamper, SettingsRepository(db, stamper))
    private val repo = DefaultSleepRepository(db, stamper, schedule, DubaiZone, clock, emptySet())
    private val store = ViewModelStore()
    private val now = MutableStateFlow(clock.nowMs)
    private val ticker = object : SleepTicker(clock) { override fun ticks(): Flow<Long> = now }

    @After fun tearDown() {
        store.clear()
        db.close()
    }

    @Test fun oneTapGoesToBedThenUp() {
        val vm = SleepViewModel(repo, schedule, ticker, clock, DubaiZone).also { store.put("vm", it) }
        rule.setContent { SleepScreen(onEdit = {}, onSettings = {}, vm = vm) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Going to bed").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Nights you log show up here.").assertIsDisplayed()
        rule.onNodeWithText("Going to bed").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("I'm up").fetchSemanticsNodes().isNotEmpty() }
        assertNotNull(runBlocking { repo.open() })
        clock.nowMs += 7 * 3_600_000L
        now.value = clock.nowMs
        rule.onNodeWithText("I'm up").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Going to bed").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("7 h").fetchSemanticsNodes().isNotEmpty() }
    }
}
