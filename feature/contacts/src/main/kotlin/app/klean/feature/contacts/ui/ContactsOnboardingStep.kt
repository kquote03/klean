package app.klean.feature.contacts.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.klean.core.ui.module.OnboardingStepProvider
import app.klean.feature.contacts.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Setup page "Contacts" (order 600, plan M16.8): Klean's contacts appear in the phone's own contacts and dialer, and
 * DAVx5 contacts appear in Klean, once contacts access is given. Asking is optional; Settings › Contacts asks later.
 */
class ContactsOnboardingStep @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val access: ContactsAccessHook,
) : OnboardingStepProvider {
    override val key = "contacts.access"
    override val order = 600
    override val titleRes = R.string.contacts_setup_title

    private fun granted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    @Composable override fun Content() {
        var allowed by remember { mutableStateOf(granted()) }
        val scope = rememberCoroutineScope()
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            allowed = granted()
            scope.launch { runCatching { access.onAccessChanged() } }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.contacts_setup_body), style = MaterialTheme.typography.bodyLarge)
            if (allowed) {
                Text(stringResource(R.string.contacts_settings_phone_on), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
            } else {
                FilledTonalButton(
                    onClick = { ask.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) },
                    modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.contacts_setup_allow)) }
            }
        }
    }
}
