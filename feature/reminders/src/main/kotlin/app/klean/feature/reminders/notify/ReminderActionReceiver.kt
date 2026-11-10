package app.klean.feature.reminders.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.klean.core.common.ApplicationScope
import app.klean.feature.reminders.ReminderActions
import app.klean.feature.reminders.Snooze
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** The buttons on reminder notifications (and the swipe). Works offline, from the lock screen. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderIntents.EXTRA_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(8_000) {
                    when (intent.action) {
                        ReminderIntents.DONE -> actions.done(id)
                        ReminderIntents.DISMISS -> actions.dismiss(id)
                        ReminderIntents.SNOOZE_10 -> actions.snooze(id, Snooze.TEN_MINUTES)
                        ReminderIntents.SNOOZE_HOUR -> actions.snooze(id, Snooze.ONE_HOUR)
                        ReminderIntents.SNOOZE_TOMORROW -> actions.snooze(id, Snooze.TOMORROW)
                        ReminderIntents.SWIPED -> actions.swiped(id)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Klean", "reminder action ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
