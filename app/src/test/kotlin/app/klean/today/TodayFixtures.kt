package app.klean.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Restaurant
import app.klean.core.ui.module.GlanceRow
import app.klean.core.ui.module.InstantResult
import app.klean.core.ui.module.QuickAction
import app.klean.shared.module.ModuleId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.LocalDate

/** A fixed day for Today's screen tests and screenshots: Tuesday 10 November 2026, 12:00 in Dubai. */
object TodayFixtures {
    val day = LocalDate(2026, 11, 10)
    private val zone = ZoneId.of("Asia/Dubai")
    fun at(h: Int, m: Int = 0): Long = java.time.LocalDateTime.of(2026, 11, 10, h, m).atZone(zone).toInstant().toEpochMilli()

    /** "1:15 PM", independent of the machine's zone. */
    val timeOf: (Long) -> String = { ms -> DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(Instant.ofEpochMilli(ms).atZone(zone)) }

    var instantRuns = 0
    var undone = 0

    fun quickActions(sleepLabel: String = "Going to bed"): List<QuickAction> = listOf(
        QuickAction.Capture(ModuleId.DIET, "Food", Icons.Rounded.Restaurant),
        QuickAction.Capture(ModuleId.TASKS, "Task", Icons.Rounded.CheckCircleOutline),
        QuickAction.Instant(ModuleId.SLEEP, sleepLabel, Icons.Rounded.Bedtime) {
            instantRuns++
            InstantResult("Good night") { undone++ }
        },
        QuickAction.Open(ModuleId.WORKOUT, "Workout", Icons.Rounded.FitnessCenter, "workout-start"),
        QuickAction.Capture(ModuleId.CALENDAR, "Event", Icons.Rounded.Event),
    )

    private fun icon(m: ModuleId) = when (m) {
        ModuleId.DIET -> Icons.Rounded.Restaurant
        ModuleId.TASKS -> Icons.Rounded.CheckCircleOutline
        ModuleId.SLEEP -> Icons.Rounded.Bedtime
        ModuleId.WORKOUT -> Icons.Rounded.FitnessCenter
        else -> Icons.Rounded.Event
    }

    private fun row(key: String, m: ModuleId, text: String, at: Long?, detail: String? = null, checkable: Boolean = false) =
        GlanceRow(key, m, text, at, detail, icon = icon(m), route = "klean://${m.value}", checkable = checkable)

    val typical = Glance(
        quickActions = quickActions(),
        attention = GlanceSection(listOf(row("c1", ModuleId.CALENDAR, "Lunch with Lina", at(12, 20), "Starts in 20 min"))),
        upcoming = GlanceSection(
            listOf(
                row("t1", ModuleId.TASKS, "Call the bank", at(15), checkable = true),
                row("c2", ModuleId.CALENDAR, "Dentist", at(17), "5:00 PM – 6:00 PM"),
            ),
        ),
        done = GlanceSection(
            listOf(
                row("s1", ModuleId.SLEEP, "Slept 7 h 20 m", at(7), "11:40 PM – 7:00 AM"),
                row("d1", ModuleId.DIET, "coffee with milk", at(8, 10)),
                row("w1", ModuleId.WORKOUT, "pushups, squats", at(9), "2 items · 25 min"),
                row("d2", ModuleId.DIET, "shawarma", at(13, 15), "chicken, garlic sauce"),
            ),
        ),
    )

    val empty = Glance(quickActions(), GlanceSection(emptyList()), GlanceSection(emptyList()), GlanceSection(emptyList()))

    fun state(g: Glance = typical, noModules: Boolean = false) = TodayState(today = day, glance = g, loaded = true, noModules = noModules)
}
