package app.klean.setup

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.klean.R
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.design.component.SwitchListItem
import app.klean.core.ui.module.OnboardingStepProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.launch

/** "Is this your main phone?" Alarms (M18 reminders) ring only on the main phone. Device-local. */
class MainPhoneStep @Inject constructor(private val prefs: DevicePrefs) : OnboardingStepProvider {
    override val key = "core.mainPhone"
    override val order = ORDER
    override val titleRes = R.string.setup_main_phone_title

    @Composable override fun Content() {
        val main by prefs.mainPhone.collectAsState(initial = true)
        val scope = rememberCoroutineScope()
        Text(
            stringResource(R.string.setup_main_phone_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        SwitchListItem(
            checked = main,
            onCheckedChange = { v -> scope.launch { prefs.setMainPhone(v) } },
            headline = { Text(stringResource(R.string.setup_main_phone_switch)) },
        )
    }

    companion object {
        const val ORDER = 300
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SetupStepsModule {
    @Binds @IntoSet abstract fun mainPhone(s: MainPhoneStep): OnboardingStepProvider
}
