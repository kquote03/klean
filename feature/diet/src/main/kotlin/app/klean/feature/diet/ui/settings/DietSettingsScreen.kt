package app.klean.feature.diet.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.diet.R

/** Placeholder until prompt settings land (M5). Registered now so the Settings entry always navigates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diet_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.diet_back))
                    }
                },
            )
        },
    ) { padding -> NeutralEmpty(stringResource(R.string.diet_settings_later), Modifier.padding(padding)) }
}
