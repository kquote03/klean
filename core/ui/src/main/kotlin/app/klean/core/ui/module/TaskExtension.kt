package app.klean.core.ui.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Something another module adds to the task screen (Delegation's \"Delegate\", M23), so Tasks never depends on
 * it. Contributed with `@IntoSet`; each one hides itself when its module is off.
 */
interface TaskExtension {
    @Composable fun Content(taskId: String, title: String, modifier: Modifier)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TaskExtensionsEntryPoint {
    fun taskExtensions(): Set<@JvmSuppressWildcards TaskExtension>
}

@dagger.Module
@InstallIn(SingletonComponent::class)
abstract class TaskExtensionsModule {
    @Multibinds abstract fun taskExtensions(): Set<TaskExtension>
}
