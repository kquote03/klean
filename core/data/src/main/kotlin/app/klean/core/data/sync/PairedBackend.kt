package app.klean.core.data.sync

/**
 * The Klean backend this phone is paired with (M25), for modules that need its address without depending on
 * `:core:sync` (e.g. Contacts, to tell whether a DAVx5 account syncs this backend). Bound in `:core:sync`.
 */
interface PairedBackend {
    /** The backend's public URL (no trailing slash), or null when not paired. */
    suspend fun url(): String?
}
