package app.klean.core.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.klean.core.design.theme.KleanTheme

/**
 * Shown by Health Connect when the user asks why Klean wants access (required for the permission request to
 * work: `ACTION_SHOW_PERMISSIONS_RATIONALE` up to Android 13, `VIEW_PERMISSION_USAGE` from Android 14).
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KleanTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.health_privacy_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.health_privacy_body), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = ::finish, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.health_privacy_close)) }
                    }
                }
            }
        }
    }
}
