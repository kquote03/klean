package app.klean.feature.calendar

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.serializer

/** Calendar's synced settings (kept in the module, not SyncedKeys, so parallel modules never collide). */
object CalendarKeys {
    /** Quiet reminder for new timed events, minutes before the start; -1 = none. */
    val DefaultReminderMinutes = SettingKey("calendar.defaultReminderMinutes", 10, Int.serializer())
    /** Show dated tasks in the calendar views. */
    val ShowTasks = SettingKey("calendar.showTasks", true, Boolean.serializer())
    /** Show reminders in the calendar views. */
    val ShowReminders = SettingKey("calendar.showReminders", true, Boolean.serializer())

    /** The reminder choices the editor and settings offer (minutes before; -1 = none). */
    val REMINDER_CHOICES = listOf(-1, 0, 10, 60, 1440)
}
