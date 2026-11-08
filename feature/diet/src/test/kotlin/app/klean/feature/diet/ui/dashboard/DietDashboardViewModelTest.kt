package app.klean.feature.diet.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.klean.core.ai.EnrichmentPolicy
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.diet.Dubai
import app.klean.feature.diet.DubaiZone
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.data.DefaultDietRepository
import app.klean.feature.diet.data.DietChangeDispatcher
import app.klean.feature.diet.inMemoryDb
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.ui.AndroidDietFormatter
import app.klean.feature.diet.ui.MinuteTicker
import app.klean.feature.diet.ui.today.DietRow
import app.klean.feature.diet.ui.today.DietViewModel
import app.klean.shared.ai.AiTask
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.atMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DietDashboardViewModelTest {
    private val nov9 = LocalDate(2026, 11, 9)
    private val clock = FakeClock(atMinute(nov9, 20 * 60, Dubai))
    private val fmt = AndroidDietFormatter(ApplicationProvider.getApplicationContext<Context>(), DubaiZone)
    private val oneTick = object : MinuteTicker() {
        override fun ticks(): Flow<Unit> = flowOf(Unit)
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun submitLogsWithDashboardSource() = runTest {
        val repo = FakeDietRepository(clock)
        val vm = DietDashboardViewModel(repo, fmt, oneTick)
        vm.onSubmit(" tea ")
        vm.onSubmit(" ")
        assertEquals(listOf(FakeDietRepository.LogCall("tea", CaptureSource.DASHBOARD, null)), repo.logs)
    }

    @Test fun showsLastFiveEntriesInTimeOrder() = runTest {
        val repo = FakeDietRepository(clock)
        repo.entries.value = (0 until 7).map { i -> repo.entry("e$i", "item $i", atMinute(nov9, (9 + i) * 60, Dubai)) }
        val vm = DietDashboardViewModel(repo, fmt, oneTick)
        vm.uiState.test {
            var s = awaitItem()
            while (s.entries.size < 5) s = awaitItem()
            assertEquals(listOf("item 2", "item 3", "item 4", "item 5", "item 6"), s.entries.map { it.text })
            assertEquals(7, s.count)
            assertEquals(fmt.time(atMinute(nov9, 15 * 60, Dubai)), s.entries.last().timeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Stand-in for the on-device check: logging from Today shows up on the Diet tab right away. */
    @Test fun loggingFromDashboardAppearsOnDietScreen() = runBlocking {
        val db = inMemoryDb()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
        val noAi = object : EnrichmentPolicy {
            override suspend fun hasProviderFor(task: AiTask) = false
        }
        val repo = DefaultDietRepository(
            db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), DietChangeDispatcher(emptySet()), noAi, scope,
        )
        val dashboard = DietDashboardViewModel(repo, fmt, oneTick)
        val diet = DietViewModel(repo, clock, fmt, oneTick, app.klean.feature.diet.FakeVoice(), app.klean.feature.diet.FakePhotos())
        diet.uiState.test {
            var s = awaitItem()
            while (!s.loaded) s = awaitItem()
            assertEquals(emptyList<DietRow>(), s.rows.filterIsInstance<DietRow.Entry>())
            dashboard.onSubmit("hummus wrap")
            do { s = awaitItem() } while (s.rows.none { it is DietRow.Entry })
            assertEquals(listOf("hummus wrap"), s.rows.filterIsInstance<DietRow.Entry>().map { it.text })
            cancelAndIgnoreRemainingEvents()
        }
        scope.cancel()
        db.close()
    }
}
