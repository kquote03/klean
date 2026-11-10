package app.klean

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.klean.core.capture.CaptureActivity
import app.klean.core.capture.CaptureLinks
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.ui.theme.KleanThemeFromPrefs
import app.klean.nav.KleanAppUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: DevicePrefs

    /** Deep links that arrive while the activity is alive (singleTop). The first intent is handled by NavHost. */
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) forwardCapture(intent)
        enableEdgeToEdge()
        setContent { KleanThemeFromPrefs(prefs) { KleanAppUi(deepLinks = newIntents) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (forwardCapture(intent)) return
        newIntents.tryEmit(intent)
    }

    /** `klean://capture?…` opened as a plain link (browser, adb, web): hand it to the capture sheet. */
    private fun forwardCapture(intent: Intent): Boolean {
        if (!CaptureLinks.isCaptureLink(intent.dataString)) return false
        startActivity(Intent(this, CaptureActivity::class.java).setData(intent.data))
        return true
    }
}
