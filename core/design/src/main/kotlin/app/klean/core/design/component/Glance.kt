package app.klean.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.klean.core.design.R
import app.klean.core.design.theme.Space

/** One tile in Today's quick-action grid. [contentDescription] defaults to [label]. */
data class QuickActionUi(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String? = null,
    val onClick: () -> Unit,
)

/** The grid's fixed shape: two rows of four (docs/ui/design-system.md › Clutter budget, Today quick actions). */
const val QUICK_ACTION_COLUMNS = 4
const val QUICK_ACTION_MAX = 8

/** A quick-action tile's height. */
val QuickActionHeight = 72.dp

/**
 * Today's one-tap grid: up to [QUICK_ACTION_MAX] tonal tiles in rows of [QUICK_ACTION_COLUMNS], each an icon over a
 * one-line label that shrinks rather than wraps (font 1.5). The first tile (Speak) sits on the primary container,
 * the rest on `surfaceContainerHigh`. The whole grid is one region for the clutter budget ([CLUTTER_EXEMPT]).
 */
@Composable
fun QuickActionGrid(actions: List<QuickActionUi>, modifier: Modifier = Modifier) {
    val shown = actions.take(QUICK_ACTION_MAX)
    Column(modifier.fillMaxWidth().testTag(CLUTTER_EXEMPT), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        shown.chunked(QUICK_ACTION_COLUMNS).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                row.forEachIndexed { c, a -> QuickActionTile(a, primary = r == 0 && c == 0, modifier = Modifier.weight(1f)) }
                // A short last row keeps the tiles the same width as the full rows above it.
                repeat(QUICK_ACTION_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickActionTile(a: QuickActionUi, primary: Boolean, modifier: Modifier) {
    val bg = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val description = a.contentDescription ?: a.label
    Column(
        modifier
            .widthIn(min = Space.touchTarget)
            .heightIn(min = QuickActionHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .clickable(role = Role.Button, onClick = a.onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Space.xxs, vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(a.icon, contentDescription = null, tint = fg)
        Spacer(Modifier.size(Space.xxs))
        Text(
            a.label,
            color = fg,
            maxLines = 1,
            // No wrapping: a label too wide shrinks (auto-size measures width only when it can't wrap).
            softWrap = false,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
        )
    }
}

/**
 * One whiteboard section: a [SectionHeader], then [content]'s rows on one Soft surface (`large` shape), then [more]
 * as a text button ("+3 more"). With no rows ([content] null), [empty] as one calm line, or nothing at all.
 * The section is one region for the clutter budget: its rows open detail.
 */
@Composable
fun GlanceSection(
    title: String,
    modifier: Modifier = Modifier,
    empty: String? = null,
    more: MenuAction? = null,
    first: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)?,
) {
    if (content == null && empty == null) return
    Column(modifier.fillMaxWidth().testTag(CLUTTER_EXEMPT)) {
        SectionHeader(title, first = first)
        if (content == null) {
            Text(
                empty.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xxs, vertical = Space.xxs),
            )
        } else {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = Space.xs),
                content = content,
            )
            if (more != null) {
                TextButton(onClick = more.onClick, modifier = Modifier.heightIn(min = Space.touchTarget)) { Text(more.label) }
            }
        }
    }
}

/** The time column's width: Tiimo's gutter, wide enough for "12:45 PM". */
val TimeGutter = 64.dp

/**
 * A whiteboard row: the [time] in a fixed gutter on the left (blank when there is none), the [icon], then the
 * [title] (at most two lines) and its [detail]. [dimmed] rows (done) read quieter; a [checkable] row ends in a
 * tick button. Tapping the row opens it ([onClick]).
 */
@Composable
fun TimelineRow(
    time: String?,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    dimmed: Boolean = false,
    checkable: Boolean = false,
    onCheck: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Space.touchTarget)
            .padding(start = Space.md, end = if (checkable) Space.xxs else Space.md, top = Space.xxs, bottom = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time.orEmpty(),
            modifier = Modifier.width(TimeGutter),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = sub,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
        )
        Spacer(Modifier.width(Space.xxs))
        Box(Modifier.size(Space.lg), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = sub, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f).padding(vertical = Space.xxs)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dimmed) sub else MaterialTheme.colorScheme.onSurface,
                maxLines = lineLimit(title, 2),
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = sub, maxLines = lineLimit(detail, 2), overflow = TextOverflow.Ellipsis)
            }
        }
        if (checkable) {
            IconButton(onClick = onCheck, modifier = Modifier.size(Space.touchTarget)) {
                Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = stringResource(R.string.design_mark_done, title), tint = sub)
            }
        }
    }
}

