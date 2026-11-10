package app.klean

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.klean.core.capture.UniversalCaptureService
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.ui.module.DashboardContributions
import app.klean.core.ui.module.ModuleRegistry
import app.klean.core.ui.module.StatusLine
import app.klean.core.ui.module.TodayItems
import app.klean.feature.workout.WorkoutSurfaces
import app.klean.feature.workout.data.WorkoutKeys
import app.klean.feature.workout.data.WorkoutRepository
import app.klean.feature.workout.suggest.WorkoutSuggestion
import app.klean.shared.module.ModuleId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * M20: switching Workout off through the real registry takes it off every surface: nav, Today (tile, status
 * line, widget items), capture, and the suggestion alarm and notification.
 */
@HiltAndroidTest
class WorkoutDisableTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var repo: WorkoutRepository
    @Inject lateinit var suggestion: WorkoutSuggestion
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var capture: UniversalCaptureService
    @Inject lateinit var status: StatusLine
    @Inject lateinit var todayItems: TodayItems
    @Inject lateinit var dashboard: DashboardContributions

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    @Before fun setUp() = runBlocking {
        hilt.inject()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(ctx.packageName, Manifest.permission.POST_NOTIFICATIONS)
        registry.setEnabled(ModuleId.WORKOUT, true)
    }

    @After fun tearDown() = runBlocking {
        settings.set(WorkoutKeys.SuggestEnabled, false)
        registry.setEnabled(ModuleId.WORKOUT, true)
        nm.cancelAll()
    }

    private suspend fun awaitNotification(present: Boolean, id: Int): Boolean {
        repeat(30) {
            if (nm.activeNotifications.any { it.id == id } == present) return true
            delay(100)
        }
        return false
    }

    @Test fun disablingWorkoutRemovesItEverywhere() = runBlocking {
        val session = repo.start()
        settings.set(WorkoutKeys.SuggestEnabled, true)
        suggestion.reschedule()
        assertNotNull("suggestion armed while on", WorkoutSuggestion.pending(ctx, create = false))
        assertTrue(suggestion.onAlarm(force = true))
        assertTrue(awaitNotification(true, WorkoutSuggestion.NOTIFICATION_ID))
        assertTrue(status.fragments(10).first().any { it.key == WorkoutSurfaces.KEY_STATUS })
        assertTrue(todayItems.items().first().any { it.key.startsWith("workout:") })
        assertTrue(dashboard.summaries().first().any { it.key == WorkoutSurfaces.KEY_TILE })

        registry.setEnabled(ModuleId.WORKOUT, false)

        assertTrue(registry.enabled.first().none { it.id == ModuleId.WORKOUT })
        assertTrue(registry.dashboardCards.first().none { it.id == ModuleId.WORKOUT })
        assertNull("suggestion alarm", WorkoutSuggestion.pending(ctx, create = false))
        assertTrue("suggestion notification", awaitNotification(false, WorkoutSuggestion.NOTIFICATION_ID))
        assertFalse(suggestion.onAlarm(force = true))
        suggestion.reschedule()
        assertNull("stays off after a reschedule", WorkoutSuggestion.pending(ctx, create = false))
        assertTrue(status.fragments(10).first().none { it.key == WorkoutSurfaces.KEY_STATUS })
        assertTrue(todayItems.items().first().none { it.key.startsWith("workout:") })
        assertTrue(dashboard.summaries().first().none { it.key == WorkoutSurfaces.KEY_TILE })
        assertTrue(capture.availableHandlers.first().none { it.moduleId == ModuleId.WORKOUT })

        repo.delete(session)
    }
}
