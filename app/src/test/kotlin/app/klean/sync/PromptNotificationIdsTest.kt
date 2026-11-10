package app.klean.sync

import app.klean.core.sync.PromptNotifications
import app.klean.feature.reminders.notify.AndroidReminderNotifier
import app.klean.feature.workout.suggest.WorkoutSuggestion
import org.junit.Assert.assertEquals
import org.junit.Test

/** `:core:sync` can't see the modules; it cancels their prompt notifications by these ids. */
class PromptNotificationIdsTest {
    @Test fun syncCancelsTheIdsTheModulesPostWith() {
        assertEquals(AndroidReminderNotifier.NOTIFICATION_ID, PromptNotifications.REMINDER_ID)
        assertEquals(WorkoutSuggestion.NOTIFICATION_ID, PromptNotifications.WORKOUT_ID)
    }
}
