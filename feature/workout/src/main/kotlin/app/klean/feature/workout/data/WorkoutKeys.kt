package app.klean.feature.workout.data

import app.klean.core.data.settings.SettingKey
import kotlinx.serialization.builtins.serializer

/** Workout's synced settings (keys live in the module). */
object WorkoutKeys {
    /** "Suggest a workout after a few quiet days". Off until the user asks for it. */
    val SuggestEnabled = SettingKey("workout.suggest.enabled", false, Boolean.serializer())
    /** How many days without a workout before one suggestion; also the least gap between suggestions. */
    val SuggestDays = SettingKey("workout.suggest.days", 3, Int.serializer())
    /** Local minute of day the suggestion may come (moved out of quiet hours). Default 18:00. */
    val SuggestMinute = SettingKey("workout.suggest.minute", 18 * 60, Int.serializer())
    /** When the last suggestion went out (UTC ms, any device), so it comes at most every N days. */
    val SuggestLastAt = SettingKey("workout.suggest.lastAt", 0L, Long.serializer())
}
