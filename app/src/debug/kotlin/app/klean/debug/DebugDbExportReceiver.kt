package app.klean.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.useWriterConnection
import app.klean.core.common.ApplicationScope
import app.klean.core.data.db.KleanDatabase
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only. `run-as` doesn't work on every test device (Waydroid), so this copies the live
 * database (WAL included, via VACUUM INTO) to the app's external files dir for `adb pull`:
 * `adb shell am broadcast -a app.klean.debug.EXPORT_DB -p app.klean.debug`, then
 * `adb pull /sdcard/Android/data/app.klean.debug/files/klean-debug.db`.
 */
@AndroidEntryPoint
class DebugDbExportReceiver : BroadcastReceiver() {
    @Inject lateinit var db: KleanDatabase
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val dir = context.getExternalFilesDir(null) ?: return@launch
                val target = File(dir, "klean-debug.db").apply { delete() }
                db.useWriterConnection { c -> c.usePrepared("VACUUM INTO ?") { s -> s.bindText(1, target.absolutePath); s.step() } }
                Log.i("Klean", "debug db exported to ${target.absolutePath}")
            } catch (e: Exception) {
                Log.w("Klean", "debug db export failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
