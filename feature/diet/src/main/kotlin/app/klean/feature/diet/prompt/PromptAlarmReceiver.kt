package app.klean.feature.diet.prompt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** The check-in alarm fired: hand the planned prompt to the engine. */
@AndroidEntryPoint
class PromptAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: DietPromptEngine
    @Inject lateinit var clock: Clock
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val plan = PromptAlarms.planFrom(intent, clock.now().toEpochMilliseconds())
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) { engine.onAlarm(plan) }
            } catch (e: Exception) {
                Log.w("Klean", "check-in alarm failed", e)
            } finally {
                pending?.finish()
            }
        }
    }
}
