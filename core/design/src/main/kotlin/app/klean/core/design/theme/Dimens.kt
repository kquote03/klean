package app.klean.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The only spacing values screens may use (docs/ui/design-system.md). `NoRawDpTest` enforces it. */
object Space {
    /** Icon-to-label gaps, tight stacks inside a row. */
    val xxs = 4.dp
    /** Between a section header and its content; chip gaps. */
    val xs = 8.dp
    /** Between cards; between a row's leading element and its text. */
    val sm = 12.dp
    /** Screen edge, card padding, row horizontal padding. */
    val md = 16.dp
    /** Between sections; under Today's header. */
    val lg = 24.dp
    /** Top of an empty state. */
    val xl = 32.dp
    /** The minimum touch target. */
    val xxl = 48.dp

    /** The gap between rows of a list group, and between blocks on a time grid. Never a padding of its own. */
    val hairline = 1.dp

    val screenEdge = md
    val cardPadding = md
    val cardGap = sm
    val sectionGap = lg
    val touchTarget = xxl
}

/**
 * small 8: chart bars, snackbars. medium 16: the Add button, form fields, timeline blocks.
 * large 24: cards and list groups. Pills use [androidx.compose.foundation.shape.CircleShape].
 */
val KleanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
