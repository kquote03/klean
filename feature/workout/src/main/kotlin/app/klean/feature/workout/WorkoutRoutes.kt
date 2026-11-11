package app.klean.feature.workout

import kotlinx.serialization.Serializable

/** Every Workout screen lives in this graph, so the nav keeps Workout selected inside it. */
@Serializable object WorkoutGraph
@Serializable object WorkoutRoute
/** [templateId] null makes a new saved workout. */
@Serializable data class TemplateEditRoute(val templateId: String? = null)
/** A running or finished session. */
@Serializable data class SessionRoute(val sessionId: String)
@Serializable object ExercisesRoute
@Serializable data class ExerciseHistoryRoute(val exerciseId: String)
@Serializable object WorkoutSettingsRoute
/** The start sheet (Today's Workout tile): saved workouts, Run, an empty workout. */
@Serializable data object WorkoutStartSheetRoute
