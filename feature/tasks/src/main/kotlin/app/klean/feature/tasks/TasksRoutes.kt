package app.klean.feature.tasks

import kotlinx.serialization.Serializable

/** Every Tasks screen lives in this graph, so the bottom bar keeps Tasks selected on the edit page. */
@Serializable object TasksGraph
/** [tab]: a [app.klean.feature.tasks.ui.TasksTab] name (any case), "review" (= Today), or null for Today. */
@Serializable data class TasksRoute(val tab: String? = null)
/** [taskId] null adds a task; [parentId] makes it a step of that task. */
@Serializable data class TaskEditRoute(val taskId: String? = null, val parentId: String? = null)
@Serializable object TasksSettingsRoute
