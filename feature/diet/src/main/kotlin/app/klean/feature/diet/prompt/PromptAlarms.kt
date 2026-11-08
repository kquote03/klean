package app.klean.feature.diet.prompt

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.klean.core.notifications.AlarmScheduler
import app.klean.shared.diet.prompt.MealLabel
import app.klean.shared.diet.prompt.PlannedPrompt
import app.klean.shared.diet.prompt.PromptTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Arms the single pending check-in alarm. An interface so engine tests can record instead. */
interface PromptAlarmScheduling {
    fun schedule(plan: PlannedPrompt)
    fun cancel()
}

/** One fixed request code, so there is only ever one pending check-in alarm. */
class PromptAlarms @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val alarms: AlarmScheduler,
) : PromptAlarmScheduling {
    override fun schedule(plan: PlannedPrompt) = alarms.schedule(plan.at, pendingIntent(plan))

    override fun cancel() = alarms.cancel(pendingIntent(null))

    private fun pendingIntent(plan: PlannedPrompt?): PendingIntent {
        val intent = Intent(ctx, PromptAlarmReceiver::class.java)
        if (plan != null) {
            intent.putExtra(EXTRA_AT, plan.at).putExtra(EXTRA_TRIGGER, plan.trigger.name)
            plan.meal?.let { intent.putExtra(EXTRA_MEAL, it.name) }
        }
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val REQUEST_CODE = 20
        const val EXTRA_AT = "at"
        const val EXTRA_TRIGGER = "trigger"
        const val EXTRA_MEAL = "meal"

        /** Reads the plan back from the alarm's intent; missing or unknown extras fall back to a plain cadence check-in. */
        fun planFrom(intent: Intent, now: Long): PlannedPrompt = PlannedPrompt(
            at = intent.getLongExtra(EXTRA_AT, now),
            trigger = intent.getStringExtra(EXTRA_TRIGGER)?.let { runCatching { PromptTrigger.valueOf(it) }.getOrNull() } ?: PromptTrigger.CADENCE,
            meal = intent.getStringExtra(EXTRA_MEAL)?.let { runCatching { MealLabel.valueOf(it) }.getOrNull() },
        )
    }
}
