package app.klean.widget

import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.shared.module.ModuleId
import app.klean.today.Glance

/** What a widget button does: start voice capture, open capture aimed at a module, or toggle sleep in place. */
enum class WidgetActionKind { VOICE, CAPTURE, SLEEP }

/** One widget button. [label] is null for Speak (the widget's own word); [module] is null for Speak. */
data class WidgetAction(val kind: WidgetActionKind, val label: String?, val module: ModuleId?)

/**
 * The home-screen Today widget, a small whiteboard: up to four one-tap buttons (Speak, Food, Task, Sleep), the next
 * [MAX_UPCOMING] things coming up, and the last [MAX_DONE] things done today, foods included. No module leads.
 */
data class TodayWidgetState(val actions: List<WidgetAction>, val upcoming: List<GlanceRow>, val done: List<GlanceRow>) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && done.isEmpty()
}

const val MAX_UPCOMING = 2
const val MAX_DONE = 3

/** The modules with a widget button, in order after Speak; a module that's off or hidden has none. */
private val WIDGET_MODULES = listOf(ModuleId.DIET, ModuleId.TASKS, ModuleId.SLEEP)

/** Built from the same [Glance] as Today, so the widget and the app never disagree. */
fun todayWidgetState(glance: Glance): TodayWidgetState = TodayWidgetState(
    actions = listOf(WidgetAction(WidgetActionKind.VOICE, null, null)) + WIDGET_MODULES.mapNotNull { m ->
        when (val a = glance.quickActions.firstOrNull { it.module == m }) {
            null -> null
            is QuickAction.Instant -> WidgetAction(WidgetActionKind.SLEEP, a.label, m)
            else -> WidgetAction(WidgetActionKind.CAPTURE, a.label, m)
        }
    },
    upcoming = glance.upcoming.rows.take(MAX_UPCOMING),
    done = glance.done.rows.takeLast(MAX_DONE),
)
