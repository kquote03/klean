package app.klean.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.R
import app.klean.core.ui.module.ModuleRegistry
import kotlinx.coroutines.launch

/**
 * "New in Klean": shown once on Today after an update adds modules. They are already on; a switch
 * turns one off right here, and Done (or dismissing) clears the list for good.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewModulesSheet(registry: ModuleRegistry, store: NewModulesStore) {
    val pending by store.pending.collectAsStateWithLifecycle(initialValue = emptySet())
    val modules = registry.all.filter { it.id in pending }
    if (modules.isEmpty()) return
    val enabled by registry.enabled.collectAsStateWithLifecycle(initialValue = registry.all)
    val scope = rememberCoroutineScope()
    val done: () -> Unit = { scope.launch { store.clear() } }
    ModalBottomSheet(onDismissRequest = done) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.new_modules_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.new_modules_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            modules.forEach { m ->
                val name = stringResource(m.labelRes)
                val on = enabled.any { it.id == m.id }
                val label = stringResource(R.string.new_modules_switch, name)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(m.icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = on,
                        onCheckedChange = { v -> scope.launch { registry.setEnabled(m.id, v) } },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            Button(onClick = done, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                Text(stringResource(R.string.new_modules_done))
            }
        }
    }
}
