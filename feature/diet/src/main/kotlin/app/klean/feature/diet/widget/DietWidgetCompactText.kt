package app.klean.feature.diet.widget

import android.content.Context
import app.klean.feature.diet.R

/** What the compact (narrow or one-row) widget says: the latest entry first, the count under it. */
data class CompactText(val primary: String, val secondary: String?)

fun compactText(ctx: Context, state: DietWidgetState): CompactText {
    val last = state.lastText
    if (state.count == 0 || last == null) return CompactText(ctx.getString(R.string.diet_widget_empty), null)
    return CompactText(last, ctx.resources.getQuantityString(R.plurals.diet_widget_count, state.count, state.count))
}
