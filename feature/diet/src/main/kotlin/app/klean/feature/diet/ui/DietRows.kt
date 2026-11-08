package app.klean.feature.diet.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.klean.core.design.component.EntryRow
import app.klean.core.design.component.GapMarker
import app.klean.core.design.component.PhotoThumbnail
import app.klean.core.design.component.ProviderChip
import app.klean.feature.diet.R
import app.klean.feature.diet.ui.today.DietRow

/** Entries and gap markers for one day. Shared by Today and the day pager. */
fun LazyListScope.dietRows(rows: List<DietRow>, onOpenEntry: (String) -> Unit, onGapAdd: (DietRow.GapRow) -> Unit) {
    items(rows, key = { it.key }) { row ->
        when (row) {
            is DietRow.Entry -> EntryRow(
                time = row.timeLabel,
                text = row.text,
                meta = entryMeta(row),
                onClick = { onOpenEntry(row.id) },
                trailing = row.photoPath?.let { path ->
                    { PhotoThumbnail(path, contentDescription = stringResource(R.string.diet_has_photo)) }
                },
            )
            is DietRow.GapRow -> GapMarker(
                label = row.label,
                actionLabel = stringResource(app.klean.core.design.R.string.design_gap_add_something),
                onAdd = { onGapAdd(row) },
            )
        }
    }
}

private fun entryMeta(row: DietRow.Entry): (@Composable () -> Unit)? {
    if (row.itemsSummary == null && row.providerName == null) return null
    return {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
