package app.klean.feature.settings.about

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.klean.feature.settings.R
import app.klean.feature.settings.SettingsSubScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the About screen shows that has to be read from the package and assets. */
data class AboutInfo(val version: String, val fontLicence: String) {
    companion object {
        /** Shipped by :core:design next to the Cantarell font files. */
        const val FONT_LICENCE_ASSET = "licenses/cantarell-OFL.txt"

        fun versionLabel(versionName: String?): String = versionName?.takeIf { it.isNotBlank() } ?: "dev"

        fun load(ctx: Context): AboutInfo {
            val name = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            val licence = runCatching { ctx.assets.open(FONT_LICENCE_ASSET).bufferedReader().use { it.readText() } }.getOrDefault("")
            return AboutInfo(versionLabel(name), licence.replace("\r\n", "\n").trim())
        }
    }
}

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val info by produceState<AboutInfo?>(null) { value = withContext(Dispatchers.IO) { AboutInfo.load(ctx) } }
    var showLicence by rememberSaveable { mutableStateOf(false) }
    val libraries = stringArrayResource(R.array.about_libraries)
    SettingsSubScaffold(R.string.settings_about, onBack) { modifier ->
        LazyColumn(modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_version)) },
                    supportingContent = { Text(info?.version.orEmpty()) },
                )
            }
            item {
                Text(
                    stringResource(R.string.about_offline_note),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { Header(R.string.about_font_title) }
            item {
                Text(
                    stringResource(R.string.about_font_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                TextButton(onClick = { showLicence = !showLicence }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(if (showLicence) R.string.about_font_licence_hide else R.string.about_font_licence_show))
                }
            }
            if (showLicence) {
                item {
                    Text(
                        info?.fontLicence.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item { Header(R.string.about_libraries_title) }
            items(libraries.toList()) { line ->
                val (name, licence) = line.split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(licence) })
            }
        }
    }
}

@Composable
private fun Header(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
