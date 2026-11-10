package app.klean.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import app.klean.wear.R
import app.klean.wear.data.WatchState
import app.klean.wear.ui.SendActivity
import app.klean.wear.ui.WatchCaptureActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Klean's dark palette for the watch, copied from `core/design/tokens/klean-color-tokens.json` (dark);
 * `WearColorsTest` fails when the token file changes and these don't.
 */
object WearColors {
    const val PRIMARY = 0xFFCDBDFF.toInt()
    const val ON_PRIMARY = 0xFF35297A.toInt()
    const val PRIMARY_CONTAINER = 0xFF463D6E.toInt()
    const val ON_PRIMARY_CONTAINER = 0xFFE6DEFF.toInt()
    const val SECONDARY_CONTAINER = 0xFF37564A.toInt()
    const val ON_SECONDARY_CONTAINER = 0xFFD4EFE3.toInt()
    const val ON_SURFACE = 0xFFE6E1E9.toInt()
    const val ON_SURFACE_VARIANT = 0xFFCAC4D0.toInt()
    const val SURFACE_CONTAINER_HIGH = 0xFF2B292F.toInt()

    /** Token name → value, for the test. */
    val byToken = mapOf(
        "primary" to PRIMARY, "onPrimary" to ON_PRIMARY, "primaryContainer" to PRIMARY_CONTAINER,
        "onPrimaryContainer" to ON_PRIMARY_CONTAINER, "secondaryContainer" to SECONDARY_CONTAINER,
        "onSecondaryContainer" to ON_SECONDARY_CONTAINER, "onSurface" to ON_SURFACE,
        "onSurfaceVariant" to ON_SURFACE_VARIANT, "surfaceContainerHigh" to SURFACE_CONTAINER_HIGH,
    )
}

/**
 * The four tiles' layouts, as pure functions of [WatchState] (tested without a watch). Calm copy, at most a
 * few rows, one primary action each.
 */
object TileLayouts {
    /** Tiles show at most this many chips or rows (bounded, like the phone's chip rows). */
    const val MAX_ROWS = 3

    fun quickLog(ctx: Context, s: WatchState): LayoutElement {
        if (!s.dietOn) return message(ctx.getString(R.string.tile_diet_off))
        val col = Column.Builder().setWidth(expand())
            .addContent(title(ctx.getString(R.string.tile_quick_log)))
            .addContent(gap())
        s.recentFoods.take(MAX_ROWS).forEachIndexed { i, food ->
            col.addContent(chip(food, "log_food_$i", sendFood(ctx, food), secondary = true)).addContent(gap(4f))
        }
        col.addContent(chip(ctx.getString(R.string.tile_speak), "speak", openCapture(ctx, module = "diet"), secondary = false))
        return col.build()
    }

    fun todayFood(ctx: Context, s: WatchState, zone: ZoneId): LayoutElement {
        if (!s.dietOn) return message(ctx.getString(R.string.tile_diet_off))
        val col = Column.Builder().setWidth(expand())
            .addContent(title(ctx.resources.getQuantityString(R.plurals.tile_today_food, s.todayFood.size, s.todayFood.size)))
            .addContent(gap())
        if (s.todayFood.isEmpty()) {
            col.addContent(body(ctx.getString(R.string.tile_nothing_yet)))
        } else {
            s.todayFood.takeLast(MAX_ROWS).forEach { f -> col.addContent(body("${time(f.at, zone)}  ${f.text}")) }
        }
        col.addContent(gap()).addContent(chip(ctx.getString(R.string.tile_add), "add_food", openCapture(ctx, module = "diet"), secondary = false))
        return col.build()
    }

    fun nextUp(ctx: Context, s: WatchState, zone: ZoneId): LayoutElement {
        val n = s.next
        val col = Column.Builder().setWidth(expand())
            .addContent(title(ctx.getString(R.string.tile_next_up)))
            .addContent(gap())
        if (n == null) {
            col.addContent(body(ctx.getString(R.string.tile_nothing_next)))
        } else {
            col.addContent(body(n.title))
            n.at?.let { col.addContent(caption(time(it, zone))) }
        }
        col.addContent(gap()).addContent(chip(ctx.getString(R.string.tile_capture), "capture", openCapture(ctx, module = null), secondary = false))
        return col.build()
    }

    fun workouts(ctx: Context, s: WatchState): LayoutElement {
        val col = Column.Builder().setWidth(expand())
        val active = s.active
        if (active != null) {
            col.addContent(title(active.name))
                .addContent(caption(ctx.resources.getQuantityString(R.plurals.tile_sets, active.sets, active.sets)))
                .addContent(gap())
                .addContent(chip(ctx.getString(R.string.tile_log_set), "log_set", openCapture(ctx, module = "workout"), secondary = false))
            return col.build()
        }
        col.addContent(title(ctx.getString(R.string.tile_workout))).addContent(gap())
        if (s.workouts.isEmpty()) return col.addContent(body(ctx.getString(R.string.tile_no_workouts))).build()
        s.workouts.take(MAX_ROWS).forEach { w ->
            col.addContent(chip(w.name, "start_${w.id}", startWorkout(ctx, w.id), secondary = true)).addContent(gap(4f))
        }
        return col.build()
    }

    fun time(at: Long, zone: ZoneId): String = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(at))

    private fun title(t: String) = Text.Builder().setText(t).setMaxLines(1)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(16f)).setColor(argb(WearColors.PRIMARY)).build()).build()

    private fun body(t: String) = Text.Builder().setText(t).setMaxLines(2)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(14f)).setColor(argb(WearColors.ON_SURFACE)).build()).build()

    private fun caption(t: String) = Text.Builder().setText(t).setMaxLines(1)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(12f)).setColor(argb(WearColors.ON_SURFACE_VARIANT)).build()).build()

    private fun message(t: String) = Box.Builder().setWidth(expand()).setHeight(expand()).addContent(body(t)).build()

    private fun gap(h: Float = 8f) = Spacer.Builder().setHeight(dp(h)).build()

    /** A pill with a 48 dp touch target. */
    private fun chip(label: String, id: String, action: ActionBuilders.Action, secondary: Boolean): LayoutElement {
        val bg = if (secondary) WearColors.SECONDARY_CONTAINER else WearColors.PRIMARY_CONTAINER
        val fg = if (secondary) WearColors.ON_SECONDARY_CONTAINER else WearColors.ON_PRIMARY_CONTAINER
        return Box.Builder()
            .setWidth(expand()).setHeight(dp(48f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder().setColor(argb(bg))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(24f)).build()).build(),
                    )
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(action).build())
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(label).build())
                    .build(),
            )
            .addContent(
                Text.Builder().setText(label).setMaxLines(1)
                    .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(14f)).setColor(argb(fg)).build()).build(),
            )
            .build()
    }

    private fun sendFood(ctx: Context, food: String) = ActionBuilders.launchAction(
        ComponentName(ctx, SendActivity::class.java),
        mapOf(SendActivity.EXTRA_KIND to ActionBuilders.stringExtra(SendActivity.KIND_FOOD), SendActivity.EXTRA_VALUE to ActionBuilders.stringExtra(food)),
    )

    private fun startWorkout(ctx: Context, id: String) = ActionBuilders.launchAction(
        ComponentName(ctx, SendActivity::class.java),
        mapOf(SendActivity.EXTRA_KIND to ActionBuilders.stringExtra(SendActivity.KIND_WORKOUT), SendActivity.EXTRA_VALUE to ActionBuilders.stringExtra(id)),
    )

    private fun openCapture(ctx: Context, module: String?) = ActionBuilders.launchAction(
        ComponentName(ctx, WatchCaptureActivity::class.java),
        module?.let { mapOf(WatchCaptureActivity.EXTRA_MODULE to ActionBuilders.stringExtra(it)) } ?: emptyMap(),
    )

}
