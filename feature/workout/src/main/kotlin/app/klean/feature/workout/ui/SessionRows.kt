package app.klean.feature.workout.ui

import app.klean.core.data.db.entity.WorkoutSetEntity
import app.klean.feature.workout.data.SessionWithSets
import app.klean.feature.workout.data.workoutKind
import app.klean.feature.workout.model.TemplateText
import app.klean.feature.workout.model.WorkoutItem
import app.klean.feature.workout.model.WorkoutKind

/** One exercise in a session: what was planned (from the saved workout) and the sets done so far. */
data class SessionRow(val exercise: String, val kind: WorkoutKind, val planned: WorkoutItem?, val done: List<WorkoutSetEntity>) {
    /** What "+1 set" logs: the plan, or a copy of the last set. */
    val next: WorkoutItem
        get() = planned?.copy(sets = 1) ?: done.last().let { WorkoutItem(it.exercise, it.workoutKind(), 1, it.reps, it.weightKg, it.durationS, it.distanceM) }
}

object SessionRows {
    /** Planned items first, in template order; then exercises only logged, in the order they were first done. */
    fun build(planned: List<WorkoutItem>, sets: List<WorkoutSetEntity>): List<SessionRow> {
        val byName = sets.sortedBy { it.position }.groupBy { it.exercise.trim().lowercase() }
        val plannedRows = planned.map { p -> SessionRow(p.exercise, p.kind, p, byName[p.exercise.trim().lowercase()].orEmpty()) }
        val plannedNames = planned.map { it.exercise.trim().lowercase() }.toSet()
        val extra = byName.filterKeys { it !in plannedNames }.values.map { l -> SessionRow(l.first().exercise, l.first().workoutKind(), null, l) }
        return plannedRows + extra
    }

    /** One set as text: "15", "15 × 40 kg", "1 min", "5 km". */
    fun setLabel(s: WorkoutSetEntity): String = when {
        s.distanceM != null -> TemplateText.distance(s.distanceM!!)
        s.durationS != null -> TemplateText.duration(s.durationS!!)
        s.reps != null && s.weightKg != null -> "${s.reps} × ${TemplateText.formatItem(WorkoutItem("", WorkoutKind.STRENGTH, weightKg = s.weightKg)).removePrefix(" @ ")}"
        s.reps != null -> s.reps.toString()
        s.weightKg != null -> TemplateText.formatItem(WorkoutItem("", WorkoutKind.STRENGTH, weightKg = s.weightKg)).removePrefix(" @ ")
        else -> "✓"
    }

    /** Distinct exercises in a session ("3 items"). */
    fun itemCount(s: SessionWithSets): Int = s.sets.map { it.exercise.trim().lowercase() }.distinct().size

    /** Minutes from start to end, or null when it ran under a minute or is still open. */
    fun minutes(s: SessionWithSets): Int? = s.session.endedAt?.let { ((it - s.session.startedAt) / 60_000).toInt() }?.takeIf { it > 0 }
}
