package app.klean.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.ui.module.ModuleRegistry
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModuleToggleRow(val id: ModuleId, @StringRes val labelRes: Int, val enabled: Boolean)

class ModulesSettingsViewModel(private val registry: ModuleRegistry) : ViewModel() {
    val rows: StateFlow<List<ModuleToggleRow>> = registry.enabled
        .map { on ->
            val onIds = on.map { it.id }.toSet()
            registry.all.map { ModuleToggleRow(it.id, it.labelRes, it.id in onIds) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: ModuleId, enabled: Boolean) {
        viewModelScope.launch { registry.setEnabled(id, enabled) }
    }
}

@Composable
fun ModulesSettingsScreen(
    registry: ModuleRegistry,
    onBack: () -> Unit,
    vm: ModulesSettingsViewModel = viewModel { ModulesSettingsViewModel(registry) },
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    SettingsSubScaffold(R.string.settings_modules_toggle, onBack) { modifier ->
        if (registry.all.isEmpty()) {
            NeutralEmpty(stringResource(R.string.settings_modules_empty), modifier)
        } else {
            LazyColumn(modifier.fillMaxSize()) {
                items(rows, key = { it.id.value }) { row ->
                    ListItem(
                        trailingContent = {
                            Switch(checked = row.enabled, onCheckedChange = { vm.setEnabled(row.id, it) })
                        },
                    ) { Text(stringResource(row.labelRes)) }
                }
            }
        }
    }
}
