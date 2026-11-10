package app.klean.today

import androidx.compose.ui.graphics.vector.ImageVector
import app.klean.core.ui.module.SummaryItem
import app.klean.core.ui.module.TodayItem
import app.klean.nav.NavGroup
import app.klean.shared.module.ModuleId

/** One line in a Today card's body: an icon, the fact, and an optional time on the right. */
data class TodayRow(val key: String, val text: String, val meta: String?, val icon: ImageVector?, val isTask: Boolean = false)

/**
 * A group's card on Today (Soft cards, docs/ui/design-system.md): a hero with one fact, and up to [MAX_ROWS] rows.
 * A null [label] or [fact] means "use the group's own wording" ("Next", "Nothing planned"), filled in by the screen.
 */
data class TodayCard(
    val group: NavGroup,
    val label: String?,
    val fact: String?,
    val supporting: String?,
    val rows: List<TodayRow>,
)

const val MAX_ROWS = 3

/**
 * Today's cards, one per navigation group with something to say, in the order of [visible] (the arranged, shown
 * modules; a group sits where its first module does). Plan leads with the next dated item; Track with Sleep;
 * People with the next birthday. Modules not in [visible] (hidden or off) contribute nothing.
 */
fun todayCards(
    visible: List<ModuleId>,
    summaries: Map<ModuleId, List<SummaryItem>>,
    items: Map<ModuleId, List<TodayItem>>,
): List<TodayCard> {
    val groups = visible.mapNotNull { NavGroup.of(it) }.distinct()
    return groups.mapNotNull { g ->
        // Track leads with last night, then food, then workouts (the prototype's order).
        val order = if (g == NavGroup.TRACK) listOf(ModuleId.SLEEP, ModuleId.DIET, ModuleId.WORKOUT) else g.members
        val mods = order.filter { it in visible }
        val tiles = mods.flatMap { summaries[it].orEmpty() }
        when (g) {
            NavGroup.PLAN -> {
                val next = mods.flatMap { items[it].orEmpty() }.sortedBy { it.sortKey }
                if (next.isNotEmpty()) {
                    TodayCard(
                        g, null, next.first().text, next.first().timeLabel,
                        next.drop(1).take(MAX_ROWS).map { TodayRow(it.key, it.text, it.timeLabel, null, isTask = it.key.startsWith("task:")) },
                    )
                } else {
                    // Nothing dated today: a calm "Nothing planned", plus the modules' own lines when there are any.
                    TodayCard(g, null, null, null, tiles.take(MAX_ROWS).map { it.row() })
                }
            }
            else -> {
                val heroModule = mods.firstOrNull { summaries[it].orEmpty().isNotEmpty() } ?: return@mapNotNull null
                val hero = summaries.getValue(heroModule).first()
                // One line per other module first, so a module with many tiles (Diet) can't crowd the rest out.
                val firsts = mods.filter { it != heroModule }.mapNotNull { summaries[it]?.firstOrNull() }
                val rest = tiles.filter { it != hero && it !in firsts }
                TodayCard(g, hero.label, hero.value, null, (firsts + rest).take(MAX_ROWS).map { it.row() })
            }
        }
    }
}

private fun SummaryItem.row() = TodayRow(key, value, null, icon)
