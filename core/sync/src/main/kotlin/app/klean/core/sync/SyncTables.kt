package app.klean.core.sync

/** Where a blob column's files live, relative to `filesDir`. */
enum class BlobDir(val path: String) {
    /** [app.klean.core.data.photo.PhotoStore.DIR]. */
    PHOTOS("photos"),
    /** GPS routes (M22). */
    ROUTES("routes"),
}

/**
 * How one Room table travels as [app.klean.shared.sync.SyncRecord]s. The columns themselves are read
 * generically (`SELECT *`); only what SQLite can't tell us is listed here:
 * - [booleans]: INTEGER columns that are Kotlin `Boolean`s, sent as JSON `true`/`false` (like the backend's DAV
 *   mapping and the web expect). `deleted` is always one.
 * - [blobs]: columns holding a file name whose bytes go through `/blobs/{sha256}`.
 * - [localColumns]: ids that only mean something on this phone (provider row ids). Never sent; an applied
 *   remote version keeps this phone's value.
 * `SyncTablesTest` checks this list against Room's exported schema and the entity classes.
 */
data class SyncTableSpec(
    val name: String,
    val booleans: Set<String> = emptySet(),
    val blobs: Map<String, BlobDir> = emptyMap(),
    val localColumns: Set<String> = emptySet(),
) {
    fun isBoolean(column: String) = column == "deleted" || column in booleans
}

/** Every synced table: each Room table with [app.klean.core.data.db.entity.SyncColumns]. */
object SyncTables {
    val ALL: List<SyncTableSpec> = listOf(
        // Devices first, so a fresh phone learns the main phone before reminders arrive.
        SyncTableSpec("device", booleans = setOf("is_main_phone")),
        SyncTableSpec("setting"),
        SyncTableSpec("food_entry", booleans = setOf("via_voice"), blobs = mapOf("photo_file" to BlobDir.PHOTOS)),
        SyncTableSpec("food_item"),
        SyncTableSpec("prompt_log"),
        SyncTableSpec("geofence", booleans = setOf("enabled")),
        SyncTableSpec("task_list", booleans = setOf("selected")),
        SyncTableSpec("task"),
        SyncTableSpec("reminder", booleans = setOf("is_alarm")),
        SyncTableSpec("event", booleans = setOf("all_day", "alarm"), localColumns = setOf("provider_event_id")),
        SyncTableSpec("contact", blobs = mapOf("photo_blob" to BlobDir.PHOTOS), localColumns = setOf("provider_raw_id")),
        SyncTableSpec("workout_template"),
        SyncTableSpec("workout_session", blobs = mapOf("route_blob" to BlobDir.ROUTES)),
        SyncTableSpec("workout_set"),
        SyncTableSpec("exercise_definition"),
        SyncTableSpec("sleep_session"),
        SyncTableSpec("sleep_schedule"),
        SyncTableSpec("delegation", booleans = setOf("auto_complete")),
        SyncTableSpec("delegation_message"),
        SyncTableSpec("approval_request"),
        SyncTableSpec("calendar_source", booleans = setOf("read_only", "selected")),
    )

    val byName: Map<String, SyncTableSpec> = ALL.associateBy { it.name }

    /** Local-only Room tables, never synced. */
    val LOCAL = setOf("change_counter", "sync_cursor")

    /**
     * Extra JSON key on records with blob columns: `{"<column>": "<sha256>"}`. Not a column, so every client
     * ignores it on apply; a receiving phone uses it to fetch the file. Documented in docs/backend-api.md.
     */
    const val BLOB_KEY = "blob_sha256"

    /** Server-only keys (unmodelled vCard/iCal properties). Clients ignore them; the server carries them over. */
    const val SERVER_PREFIX = "dav_"
}
