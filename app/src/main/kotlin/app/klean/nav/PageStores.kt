package app.klean.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One view-model store per pager page, kept while the shell lives (it survives rotation as a view model of the
 * activity). A `hiltViewModel()` inside a page lands in that page's store, so pages never share or clash over view
 * models, and swiping away and back doesn't recreate them. A page whose module is turned off loses its store.
 */
class PageStores : ViewModel() {
    private val owners = mutableMapOf<String, ScopedViewModelOwner>()

    /** The activity the stores create view models through; the shell sets it on every composition. */
    var parent: ViewModelStoreOwner? = null

    internal fun owner(key: String): ViewModelStoreOwner =
        owners.getOrPut(key) { ScopedViewModelOwner { checkNotNull(parent) { "no parent owner yet" } } }

    /** Clears the stores of pages that are gone (their module was turned off). */
    fun retain(keys: Set<String>) {
        (owners.keys - keys).forEach { owners.remove(it)?.viewModelStore?.clear() }
    }

    /** Pages holding a store, for tests. */
    val keys: Set<String> get() = owners.keys.toSet()

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
        parent = null
    }
}
