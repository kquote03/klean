package app.klean.feature.diet.data

import android.util.Log
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** What changed in diet data. Listeners refresh the widget, notification, prompts and AI queue. */
sealed interface DietChange {
    data class Logged(val id: String, val text: String) : DietChange
    data class Edited(val id: String) : DietChange
    data class Deleted(val id: String) : DietChange
    data object Refresh : DietChange
}

fun interface DietChangeListener {
    suspend fun onChange(change: DietChange)
}

/** Runs every listener concurrently and waits for all. One failing listener never affects the others or the write. */
class DietChangeDispatcher @Inject constructor(
    private val listeners: Set<@JvmSuppressWildcards DietChangeListener>,
) {
    suspend fun dispatch(change: DietChange) = coroutineScope {
        listeners.map { l ->
            launch {
                try {
                    l.onChange(change)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("Klean", "Diet change listener failed", e)
                }
            }
        }.joinAll()
    }
}

/** Declares the listener set so it is legal while no listener exists yet (M4 adds them). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DietChangeListenersModule {
    @Multibinds abstract fun listeners(): Set<DietChangeListener>
}
