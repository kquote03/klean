package app.klean.core.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The design system for Glance widgets (Klean Today, Diet), which can't use the Compose components: the same
 * spacing, shape radii, type sizes and card colour as the app's Soft cards (docs/ui/design-system.md), so a widget
 * reads as a small `ModuleCard`. `WidgetTokensTest` keeps the widgets on these values.
 */
object WidgetTokens {
    /** The widget is a card: `large` corners, card padding. */
    val cardRadius: Dp = 24.dp
    val cardPadding: Dp = Space.sm

    /** The capture pill: a full pill, a touch target tall, on `primaryContainer`. */
    val pillHeight: Dp = Space.touchTarget
    val pillRadius: Dp = Space.touchTarget / 2
    val pillPadding: Dp = Space.md

    /** Between the header, the pill and the rows; inside a row. */
    val gap: Dp = Space.xs
    val rowPadding: Dp = Space.xxs
    val icon: Dp = Space.touchTarget

    /** Type roles: the date / title (titleMedium), a row (bodyLarge), its time (bodyMedium), the pill (labelLarge). */
    val title: TextUnit = KleanTypography.titleMedium.fontSize
    val body: TextUnit = KleanTypography.bodyLarge.fontSize
    val meta: TextUnit = KleanTypography.bodyMedium.fontSize
    val label: TextUnit = KleanTypography.labelLarge.fontSize

    /** The card colour token (cards and list groups are `surfaceContainerLow`). */
    const val SURFACE = "surfaceContainerLow"
}
