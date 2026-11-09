package app.klean.feature.diet.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.feature.diet.R
import kotlinx.coroutines.launch

/** The one-time offer on the Diet screen (Task 12.2). Shows nothing once answered. */
@Composable
fun DeliveryOfferCard(modifier: Modifier = Modifier, vm: DeliveryOfferViewModel = hiltViewModel()) {
    val visible by vm.visible.collectAsStateWithLifecycle()
    if (!visible) return
    val ctx = LocalContext.current
    // A plain card: KleanCard's header has an "Open" affordance, and there is nothing to open here.
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.delivery_offer_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.delivery_offer_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { vm.decline() }) { Text(stringResource(R.string.delivery_offer_no)) }
                TextButton(onClick = { vm.snooze() }) { Text(stringResource(R.string.delivery_offer_later)) }
                FilledTonalButton(onClick = {
                    vm.accept()
                    runCatching { ctx.startActivity(DeliveryIntents.listenerSettings(ctx)) }
                }) { Text(stringResource(R.string.delivery_offer_set_up)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryAppsScreen(onBack: () -> Unit, vm: DeliveryAppsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.delivery_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.delivery_apps_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Section(stringResource(R.string.delivery_apps_packages))
            state.packages.forEach { pkg ->
                RemovableRow(pkg, stringResource(R.string.delivery_apps_remove, pkg)) { vm.removePackage(pkg) }
            }
            AddRow(
                label = stringResource(R.string.delivery_apps_add_package),
                invalid = stringResource(R.string.delivery_apps_package_invalid),
                onAdd = { text, done -> scope.launch { done(vm.addPackage(text).await()) } },
            )
            Section(stringResource(R.string.delivery_apps_words))
            Text(
                stringResource(R.string.delivery_apps_words_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            state.words.forEach { w ->
                RemovableRow(w, stringResource(R.string.delivery_apps_remove, w)) { vm.removeWord(w) }
            }
            AddRow(
                label = stringResource(R.string.delivery_apps_add_word),
                invalid = null,
                onAdd = { text, done -> scope.launch { done(vm.addWord(text).await()) } },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RemovableRow(text: String, removeLabel: String, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        trailingContent = {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = removeLabel) }
        },
    )
}

/** A field plus Add. [onAdd] reports back whether it was added; the field clears on success. */
@Composable
private fun AddRow(label: String, invalid: String?, onAdd: (String, (Boolean) -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var showInvalid by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; showInvalid = false },
            label = { Text(label) },
            singleLine = true,
            supportingText = if (showInvalid && invalid != null) { { Text(invalid) } } else null,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            onAdd(text) { ok -> if (ok) text = "" else showInvalid = true }
        }, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.delivery_apps_add)) }
    }
}
