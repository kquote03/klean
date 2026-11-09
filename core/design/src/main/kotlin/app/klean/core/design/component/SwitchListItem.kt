package app.klean.core.design.component

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import app.klean.core.design.theme.KleanTheme

/**
 * A settings row with a switch. The whole row toggles (label, summary and switch), and TalkBack
 * reads it as one Switch. Use this for every on/off setting instead of a ListItem with a bare Switch.
 */
@Composable
fun SwitchListItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        headlineContent = headline,
        supportingContent = supporting,
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Preview
@Composable
private fun SwitchListItemPreview() {
    KleanTheme {
        SwitchListItem(checked = true, onCheckedChange = {}, headline = { Text("Check-ins") }, supporting = { Text("About every 6 hours") })
    }
}
