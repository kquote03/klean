package app.klean.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import app.klean.core.design.theme.Space

/** Which tonal container a card's hero uses (docs/ui/design-system.md, module accents). */
enum class CardTone { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun CardTone.container(): Color = when (this) {
    CardTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    CardTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    CardTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
fun CardTone.onContainer(): Color = when (this) {
    CardTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    CardTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    CardTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

/**
 * The card on Today and at the top of module screens: a tonal hero with a small [label] ("Next", "Last night"),
 * one big [fact] and an optional [supporting] line, then an optional [body] of up to four rows on
 * `surfaceContainerLow`. The whole card is one click target ([onClick]); nothing inside it is separately tappable.
 * An optional single [action] shows as a tonal button (not on Today).
 */
@Composable
fun ModuleCard(
    label: String,
    fact: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    tone: CardTone = CardTone.PRIMARY,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    action: MenuAction? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column {
            Surface(color = tone.container(), contentColor = tone.onContainer(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Space.cardPadding, vertical = Space.md)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(fact, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (body != null) {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) { body() }
            }
            if (action != null) {
                FilledTonalButton(
                    onClick = action.onClick,
                    modifier = Modifier.padding(start = Space.cardPadding, end = Space.cardPadding, bottom = Space.cardPadding)
                        .heightIn(min = Space.touchTarget),
                ) { Text(action.label) }
            }
        }
    }
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val click = if (onClick != null) {
        Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth().clip(shape).then(click)) { content() }
}
