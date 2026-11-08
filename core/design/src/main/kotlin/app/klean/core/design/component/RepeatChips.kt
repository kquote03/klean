package app.klean.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.klean.core.design.R

/**
 * Quick-repeat chips: one tap logs that text again. Used by the capture sheet, the diet screen and
 * (later) the unlock sheet. Each chip reads "Log … again" to screen readers; touch targets are 48 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatChips(
    items: List<String>,
    onRepeat: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (items.isEmpty()) return
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                val desc = stringResource(R.string.design_repeat_again, item)
                SuggestionChip(
                    onClick = { onRepeat(item) },
                    label = { Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc },
                )
            }
        }
    }
}
