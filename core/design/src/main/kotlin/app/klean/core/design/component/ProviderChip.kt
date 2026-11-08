package app.klean.core.design.component

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.klean.core.design.theme.KleanTheme

@Composable
fun ProviderChip(name: String, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(name, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun ProviderChipPreview() {
    KleanTheme { ProviderChip("On device") }
}
