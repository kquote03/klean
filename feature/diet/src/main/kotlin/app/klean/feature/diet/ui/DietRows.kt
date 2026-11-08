package app.klean.feature.diet.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.GapMarker
import app.klean.core.design.component.ProviderChip
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.today.DietRow

/** Entries and gap markers for one day. Shared by Today and (M7) the day pager. */
fun LazyListScope.dietRows(rows: List<DietRow>, onOpenEntry: (String) -> Unit, onGapAdd: () -> Unit) {
    items(rows, key = { it.key }) { row ->
        when (row) {
            is DietRow.Entry -> EntryRow(
                time = row.timeLabel,
                text = row.text,
                meta = entryMeta(row),
                onClick = { onOpenEntry(row.id) },
            )
            is DietRow.GapRow -> GapMarker(
                label = row.label,
                actionLabel = stringResource(app.klean.core.design.R.string.design_gap_add_something),
                onAdd = onGapAdd,
            )
        }
    }
}

private fun entryMeta(row: DietRow.Entry): (@Composable () -> Unit)? {
    if (row.itemsSummary == null && row.providerName == null && !row.hasPhoto) return null
    return {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.hasPhoto) {
                Icon(
                    Icons.Rounded.Image,
                    contentDescription = stringResource(R.string.diet_has_photo),
                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.itemsSummary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            row.providerName?.let { ProviderChip(it, Modifier.padding(start = 8.dp)) }
        }
    }
}
