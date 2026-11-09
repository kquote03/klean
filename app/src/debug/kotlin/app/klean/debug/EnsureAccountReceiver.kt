package app.klean.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.core.data.account.KleanAccount
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only, until Calendar/Contacts call [KleanAccount.ensure] themselves:
 * `adb shell am broadcast -a app.klean.debug.ENSURE_ACCOUNT -p app.klean.debug`, then
 * `adb shell dumpsys account | grep app.klean.debug`.
 */
@AndroidEntryPoint
class EnsureAccountReceiver : BroadcastReceiver() {
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val account = KleanAccount.ensure(app)
                Log.i("Klean", "account ensured: $account")
            } catch (e: Exception) {
                Log.w("Klean", "ensure account failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
