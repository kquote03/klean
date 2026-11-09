package app.klean.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.core.ui.module.ModuleRegistry
import app.klean.nav.NewModulesStore
import app.klean.shared.module.ModuleId
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only: pretends an update just added modules, so "New in Klean" shows on the next Today.
 * `adb shell am broadcast -a app.klean.debug.NEW_MODULES -p app.klean.debug [--es ids tasks,calendar]`.
 * Without `ids` it lists every registered module.
 */
@AndroidEntryPoint
class NewModulesReceiver : BroadcastReceiver() {
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject lateinit var registry: ModuleRegistry
    @Inject lateinit var store: NewModulesStore

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val ids = intent.getStringExtra("ids")?.split(',')?.map { ModuleId(it.trim()) }?.toSet()
            ?: registry.all.map { it.id }.toSet()
        scope.launch {
            try {
                store.add(ids)
                Log.i("Klean", "pending new modules: $ids")
            } finally {
                pending.finish()
            }
        }
    }
}
