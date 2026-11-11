package app.klean.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.klean.core.design.theme.Space

/**
 * A small summary card that only informs (Track › Week): a [label], one [fact] and an optional [supporting] line on
 * `surfaceContainerLow`, `large` shape. Not tappable: the group's tabs already lead to each module.
 */
@Composable
fun FactCard(label: String, fact: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.cardPadding), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fact, style = MaterialTheme.typography.headlineSmall, maxLines = lineLimit(fact, 2), overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
