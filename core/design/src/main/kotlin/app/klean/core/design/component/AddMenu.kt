package app.klean.core.design.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.klean.core.design.R

/** One line of the Add menu: "Task", "Log food"… */
data class AddMenuItem(val key: String, val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The Add menu's content. [onCapture] opens the capture sheet ("Type or speak…"); [items] are listed top to
 * bottom, so the caller puts the most common one last, nearest the button.
 */
data class AddMenuSpec(val items: List<AddMenuItem>, val onCapture: () -> Unit)

/**
 * The single Add button and its menu (Material 3 Expressive FAB menu). The first item is "Type or speak…",
 * which opens the capture sheet; the rest add to one module each. Choosing an item closes the menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddMenu(spec: AddMenuSpec, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val addLabel = stringResource(R.string.design_add)
    val closeLabel = stringResource(R.string.design_add_close)
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) closeLabel else addLabel
                    stateDescription = ""
                },
            ) {
                val icon = if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.animateIcon(
                        { checkedProgress },
                        color = ToggleFloatingActionButtonDefaults.iconColor(
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            MaterialTheme.colorScheme.onPrimary,
                        ),
                    ),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { onExpandedChange(false); spec.onCapture() },
            text = { Text(stringResource(R.string.design_type_or_speak)) },
            icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        spec.items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = { onExpandedChange(false); item.onClick() },
                text = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
}
