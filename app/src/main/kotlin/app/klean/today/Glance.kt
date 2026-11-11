package app.klean.today

import app.klean.core.ui.module.GlanceContribution
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.QuickAction
import app.klean.shared.module.ModuleId

/** One whiteboard section: the rows shown, and how many more there are ("+3 more"). */
data class GlanceSection(val rows: List<GlanceRow>, val moreCount: Int = 0)

/**
 * Today's whiteboard: the modules' one-tap actions, then what needs attention, what's coming up and what has
 * happened so far today. Sections are plain lists ordered by time; no module ranks above another.
 */
data class Glance(
    val quickActions: List<QuickAction>,
    val attention: GlanceSection,
    val upcoming: GlanceSection,
    val done: GlanceSection,
)

/** The grid holds eight tiles in two rows of four; Speak and Type always take two of them. */
const val MAX_QUICK_MODULE_ACTIONS = 6
const val MAX_ATTENTION = 5
const val MAX_UPCOMING = 6

/**
 * Merges the [visible] modules' [parts] (Arrange Today's order; hidden or switched-off modules are absent) at [now].
 * Quick actions keep [visible]'s order; rows are ordered by time across modules. Upcoming drops what has already
 * started; rows without a time sort last there, and first in the other sections.
 */
fun buildGlance(visible: List<ModuleId>, parts: Map<ModuleId, GlanceContribution>, now: Long): Glance {
    val shown = visible.distinct().mapNotNull { id -> parts[id]?.let { id to it } }
    fun section(rows: List<GlanceRow>, max: Int) = GlanceSection(rows.take(max), (rows.size - max).coerceAtLeast(0))
    val upcoming = shown.flatMap { it.second.upcoming }.filter { (it.at ?: Long.MAX_VALUE) >= now }.sortedBy { it.at ?: Long.MAX_VALUE }
    val done = shown.flatMap { it.second.done }.sortedBy { it.at ?: Long.MIN_VALUE }
    val attention = shown.flatMap { it.second.attention }.sortedBy { it.at ?: Long.MIN_VALUE }
    return Glance(
        quickActions = shown.mapNotNull { it.second.quickAction }.take(MAX_QUICK_MODULE_ACTIONS),
        attention = section(attention, MAX_ATTENTION),
        upcoming = section(upcoming, MAX_UPCOMING),
        // The day so far is never truncated: it is the point of the screen.
        done = GlanceSection(done),
    )
}
