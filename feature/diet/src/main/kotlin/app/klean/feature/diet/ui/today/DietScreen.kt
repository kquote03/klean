package app.klean.feature.diet.ui.today

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.klean.core.design.component.NeutralEmpty
import app.klean.feature.diet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.diet_today_title)) }) }) { padding ->
        NeutralEmpty(stringResource(R.string.diet_today_empty), Modifier.padding(padding))
    }
}
