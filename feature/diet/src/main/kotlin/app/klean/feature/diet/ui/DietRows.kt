package app.klean.feature.diet.ui

import app.klean.core.design.theme.Space
import androidx.compose.foundation.layout.Column
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
    if (row.itemLines.isEmpty() && row.providerId == null) return null
    return { AiItemsBlock(row.itemLines, row.providerId) }
}

/** AI items under an entry's raw text, with the chip naming the provider that produced them. */
@Composable
fun AiItemsBlock(lines: List<String>, providerId: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f, fill = false)) {
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        providerLabelRes(providerId)?.let { ProviderChip(stringResource(it), Modifier.padding(start = Space.xs)) }
    }
}
