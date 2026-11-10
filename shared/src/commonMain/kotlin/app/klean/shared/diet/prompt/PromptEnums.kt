package app.klean.shared.diet.prompt

/** [REMINDER]: a reminder ping (M18), logged by the reminders module for its own format rotation. */
enum class PromptTrigger { CADENCE, LEARNED_MEAL, DELIVERY, GEOFENCE, UNLOCK_SHEET, REMINDER }
enum class PromptFormat { STANDARD, HEADS_UP, SILENT_VIBRATE, WIDGET_HIGHLIGHT }
enum class PromptOutcome { PENDING, LOGGED, DISMISSED, IGNORED }
enum class MealLabel { BREAKFAST, LUNCH, DINNER, LATE }
data class MealSlot(val centerMinute: Int, val promptMinute: Int, val label: MealLabel)
