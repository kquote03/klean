package app.klean.feature.diet.geofence

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.klean.core.data.place.Place
import app.klean.core.data.place.PlaceDraft
import app.klean.core.design.component.NeutralEmpty
import app.klean.core.design.component.SwitchListItem
import app.klean.feature.diet.R

/** Location permission as the Places screen sees it. Refreshed on every resume. */
private data class LocationAccess(val fine: Boolean, val background: Boolean)

@Composable
private fun rememberLocationAccess(onChange: () -> Unit): LocationAccess {
    val ctx = LocalContext.current
    fun read() = LocationAccess(ProximityAlertBackend.hasFineLocation(ctx), ProximityAlertBackend.hasBackgroundLocation(ctx))
    var access by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = read()
        if (now != access) onChange()
        access = now
    }
    return access
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacesScaffold(title: Int, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

/** Task 15.1: the places the user named, with their switches; permission asks are just in time and explained. */
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpen: (String?) -> Unit, vm: PlacesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val access = rememberLocationAccess(onChange = { vm.rewatch() })
    val askFine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.rewatch() }
    // On Android 11+ this request takes the user to the settings page rather than showing a dialog.
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        vm.rewatch()
    }
    PlacesScaffold(R.string.places_title, onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.places_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (s.places.isNotEmpty() && !access.fine) {
                PermissionCard(
                    text = stringResource(R.string.places_need_location),
                    button = stringResource(R.string.places_allow_location),
                    onClick = { askFine.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                )
            } else if (s.places.isNotEmpty() && !access.background && Build.VERSION.SDK_INT >= 29) {
                PermissionCard(
                    text = stringResource(R.string.places_need_background),
                    button = stringResource(R.string.places_open_settings),
                    onClick = { askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                )
            }
            if (s.loaded && s.places.isEmpty()) NeutralEmpty(stringResource(R.string.places_empty))
            s.places.forEach { p -> PlaceRow(p, onOpen = { onOpen(p.id) }, onEnabled = { vm.setEnabled(p.id, it) }) }
            FilledTonalButton(onClick = { onOpen(null) }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.places_add)) }
            if (s.debug && s.places.isNotEmpty()) {
                Text(
                    stringResource(R.string.diet_debug_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                s.places.forEach { p ->
                    OutlinedButton(onClick = { vm.simulateLeaving(p) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.places_debug_simulate, p.label))
                    }
                }
                s.simulated?.let { (label, asked) ->
                    Text(
                        stringResource(if (asked) R.string.places_debug_asked else R.string.places_debug_not_asked, label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun PlaceRow(p: Place, onOpen: () -> Unit, onEnabled: (Boolean) -> Unit) {
    val name = p.label
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(name) },
        supportingContent = { Text(stringResource(R.string.places_radius_value, p.radiusM)) },
        trailingContent = {
            Switch(
                checked = p.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = name },
            )
        },
    )
}

/** Add or edit a place: a name, a spot (current location or typed coordinates), a radius. No map. */
@Composable
fun PlaceEditScreen(onBack: () -> Unit, vm: PlaceEditViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(s.done) { if (s.done) onBack() }
    val askFine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.useCurrentLocation() else vm.onLocationDenied()
    }
    PlacesScaffold(if (s.isNew) R.string.places_add else R.string.places_edit, onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = s.label,
                onValueChange = vm::onLabel,
                label = { Text(stringResource(R.string.places_name)) },
                placeholder = { Text(stringResource(R.string.places_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.places_where), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    if (ProximityAlertBackend.hasFineLocation(ctx)) {
                        vm.useCurrentLocation()
                    } else {
                        askFine.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                },
                enabled = !s.locating,
            ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(if (s.locating) R.string.places_locating else R.string.places_use_current))
            }
            Text(stringResource(R.string.places_location_why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.latitude,
                    onValueChange = vm::onLatitude,
                    label = { Text(stringResource(R.string.places_latitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = s.longitude,
                    onValueChange = vm::onLongitude,
                    label = { Text(stringResource(R.string.places_longitude)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            val radiusLabel = stringResource(R.string.places_radius, s.radiusM)
            Text(radiusLabel, style = MaterialTheme.typography.titleSmall)
            Slider(
                value = s.radiusM.toFloat(),
                onValueChange = { vm.onRadius((Math.round(it / 50f) * 50)) },
                valueRange = PlaceDraft.MIN_RADIUS_M.toFloat()..PlaceDraft.MAX_RADIUS_M.toFloat(),
                steps = (PlaceDraft.MAX_RADIUS_M - PlaceDraft.MIN_RADIUS_M) / 50 - 1,
                modifier = Modifier.semantics { contentDescription = radiusLabel },
            )
            SwitchListItem(
                checked = s.enabled,
                onCheckedChange = vm::onEnabled,
                headline = { Text(stringResource(R.string.places_ask_when_leaving)) },
            )
            s.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.save() }, enabled = s.canSave) { Text(stringResource(R.string.places_save)) }
                if (!s.isNew) TextButton(onClick = { vm.delete() }) { Text(stringResource(R.string.places_delete)) }
            }
        }
    }
}
