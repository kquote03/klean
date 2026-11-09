package app.klean.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.klean.R
import app.klean.core.ui.module.KleanModule

/** The modules that don't fit in the bottom bar, in nav order, then Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(modules: List<KleanModule>, onOpen: (Any) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(modules, key = { it.id.value }) { module ->
                ListItem(
                    headlineContent = { Text(stringResource(module.labelRes)) },
                    // The row's text already names it; the icon is decoration.
                    leadingContent = { Icon(module.icon, contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(module.startRoute) },
                )
            }
            item(key = "settings") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.nav_settings)) },
                    leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(SettingsRoute) },
                )
            }
        }
    }
}
