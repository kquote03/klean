package app.klean.core.design.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.klean.core.design.theme.Space

/**
 * Switches modules inside a navigation group (Tasks · Calendar). Pill-shaped, outlined, the selected segment in
 * the group's container colour, 48 dp tall. Stateless: the shell remembers the last choice per group.
 */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.heightIn(min = Space.touchTarget),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
