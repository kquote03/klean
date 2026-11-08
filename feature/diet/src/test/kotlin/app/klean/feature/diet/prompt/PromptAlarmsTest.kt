package app.klean.feature.diet.prompt

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.notifications.AlarmScheduler
import app.klean.shared.diet.prompt.MealLabel
import app.klean.shared.diet.prompt.PlannedPrompt
import app.klean.shared.diet.prompt.PromptTrigger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class PromptAlarmsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val am = ctx.getSystemService(AlarmManager::class.java)
    private val alarms = PromptAlarms(ctx, AlarmScheduler(ctx))

    @After fun tearDown() = ShadowAlarmManager.reset()

    @Test fun onlyOnePendingAlarmCarryingThePlan() {
        alarms.schedule(PlannedPrompt(1_000L, PromptTrigger.CADENCE, null))
        alarms.schedule(PlannedPrompt(2_000L, PromptTrigger.LEARNED_MEAL, MealLabel.LUNCH))
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(1, scheduled.size)
        assertEquals(2_000L, scheduled.single().triggerAtMs)
        val intent = shadowOf(scheduled.single().operation).savedIntent
        assertEquals(PromptAlarmReceiver::class.java.name, intent.component?.className)
        assertEquals(PlannedPrompt(2_000L, PromptTrigger.LEARNED_MEAL, MealLabel.LUNCH), PromptAlarms.planFrom(intent, 0))
    }

    @Test fun cancelRemovesIt() {
        alarms.schedule(PlannedPrompt(1_000L, PromptTrigger.CADENCE, null))
        alarms.cancel()
        assertNull(shadowOf(am).peekNextScheduledAlarm())
    }

    @Test fun badExtrasFallBackToCadence() {
        val plan = PromptAlarms.planFrom(android.content.Intent().putExtra(PromptAlarms.EXTRA_TRIGGER, "NOPE"), 5L)
        assertEquals(PlannedPrompt(5L, PromptTrigger.CADENCE, null), plan)
    }
}
