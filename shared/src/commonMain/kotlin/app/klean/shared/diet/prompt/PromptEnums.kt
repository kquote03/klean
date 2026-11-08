package app.klean.shared.diet.prompt

enum class PromptTrigger { CADENCE, LEARNED_MEAL, DELIVERY, GEOFENCE, UNLOCK_SHEET }
enum class PromptFormat { STANDARD, HEADS_UP, SILENT_VIBRATE, WIDGET_HIGHLIGHT }
enum class PromptOutcome { PENDING, LOGGED, DISMISSED, IGNORED }
enum class MealLabel { BREAKFAST, LUNCH, DINNER, LATE }
data class MealSlot(val centerMinute: Int, val promptMinute: Int, val label: MealLabel)
