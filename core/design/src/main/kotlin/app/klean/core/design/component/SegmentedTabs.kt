package app.klean.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
                // Far less side padding than the default 12 dp, so four labels fit at large font scales beside the Add button
                // in the bottom toolbar.
                contentPadding = PaddingValues(horizontal = Space.xxs),
                modifier = Modifier.heightIn(min = Space.touchTarget),
            ) {
                // Four segments at font scale 1.5 don't fit: the label shrinks to fit (down to 8 sp, like the bar's labels) before it
                // ends in "…", so "Workout" never reads as "Wo…" (seen on Waydroid in Track).
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                )
            }
        }
    }
}

private val MIN_LABEL = 8.sp
