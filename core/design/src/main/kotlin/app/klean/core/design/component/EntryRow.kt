package app.klean.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.klean.core.design.theme.KleanTheme

@Composable
fun EntryRow(
    time: String,
    text: String,
    meta: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** E.g. a photo thumbnail at the end of the row. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // One time column for every row, so titles line up; a time range wraps onto two lines.
            modifier = Modifier.width(with(LocalDensity.current) { TIME_COLUMN.toDp() } + 12.dp).padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            if (meta != null) meta()
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 12.dp)) { trailing() }
        }
    }
}

/** Fits "12:45 PM"; in sp so it grows with the font scale. */
private val TIME_COLUMN = 68.sp

@Preview(showBackground = true)
@Composable
private fun EntryRowPreview() {
    KleanTheme {
        Column {
            EntryRow("08:15", "Oat porridge with banana", meta = null, onClick = {})
            EntryRow("13:02", "Chicken wrap", meta = { ProviderChip("Gemini") }, onClick = {})
        }
    }
}
