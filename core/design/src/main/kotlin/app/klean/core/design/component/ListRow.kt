package app.klean.core.design.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.Space

/**
 * One row: an optional [leading] element (check, avatar, dot, icon), a [title] with an optional [supporting] line,
 * and trailing [meta] text or a [trailing] element. 56 dp tall at least; 72 with a supporting line.
 * A row with [onClick] opens detail; [onLongClick] opens its menu (edit, delete…). [dense] rows (48 dp) sit in a card's body.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    muted: Boolean = false,
    titleMaxLines: Int = 2,
    dense: Boolean = false,
) {
    val click = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClickLabel = onClickLabel, onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }
    Row(
        modifier.fillMaxWidth().then(click).heightIn(min = if (dense) Space.touchTarget else if (supporting != null) 72.dp else 56.dp)
            .padding(start = if (leading != null) Space.xxs else Space.md, end = Space.md, top = if (dense) 0.dp else Space.xs, bottom = if (dense) 0.dp else Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        if (leading != null) {
            Box(Modifier.heightIn(min = Space.touchTarget).widthIn(min = Space.touchTarget), contentAlignment = Alignment.Center) { leading() }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (muted) TextDecoration.LineThrough else null,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** The shape of row [index] of [count] in a list group: the group's outer corners are `large`, inner ones square. */
fun groupRowShape(index: Int, count: Int): Shape {
    val r = 24.dp
    return RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp,
        topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp,
        bottomEnd = if (index == count - 1) r else 0.dp,
    )
}

/** A row's surface inside a list group: `surfaceContainerLow`, a 1 dp gap above (not a divider). */
@Composable
fun Modifier.groupRow(index: Int, count: Int): Modifier =
    padding(top = if (index > 0) 1.dp else 0.dp).clip(groupRowShape(index, count)).background(MaterialTheme.colorScheme.surfaceContainerLow)

/** [items] as one list group on a `large` surface, lazily. */
fun <T> LazyListScope.listGroup(key: String, items: List<T>, itemKey: (T) -> Any, row: @Composable (T) -> Unit) {
    itemsIndexed(items, key = { _, it -> "$key/${itemKey(it)}" }) { i, item ->
        Box(Modifier.fillMaxWidth().groupRow(i, items.size)) { row(item) }
    }
}

/** [items] as one list group, for short lists outside a lazy list. */
@Composable
fun <T> ListGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item -> Box(Modifier.fillMaxWidth().groupRow(i, items.size)) { row(item) } }
    }
}
