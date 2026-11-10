package app.klean.feature.tasks.data

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.serializer

/** Tasks' synced settings (module plan decision 2: keys live in the module, not in SyncedKeys). */
object TasksKeys {
    /** "Personal" and "Work" were created once; a deleted default list stays deleted. */
    val Seeded = SettingKey("tasks.seeded", false, Boolean.serializer())
    /** Where captured and new tasks go. */
    val DefaultListId = SettingKey("tasks.defaultListId", DefaultTasksRepository.PERSONAL_ID, String.serializer())
    /** The once-a-day "Still want these?" notification. The review on the Today tab is always there. */
    val ReviewEnabled = SettingKey("tasks.review.enabled", true, Boolean.serializer())
    /** Local minute of day the review notification comes (moved out of quiet hours). Default 10:00. */
    val ReviewMinute = SettingKey("tasks.review.minute", 600, Int.serializer())
    /** ISO date the review notification last showed, so it comes at most once a day on any device. */
    val ReviewShownOn = SettingKey("tasks.review.shownOn", "", String.serializer())
}
