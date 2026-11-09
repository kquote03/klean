package app.klean.backend

import java.io.File

/**
 * Everything the server reads from the environment. Only `KLEAN_*` variables are used; every one has a default,
 * so `docker run klean-backend:dev` works with a volume on /data and nothing else.
 */
data class KleanConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    /** Holds the SQLite database (default), blobs and generated keys. */
    val dataDir: File,
    /** JDBC URL. Default `jdbc:sqlite:<dataDir>/klean.sqlite`; `jdbc:postgresql://…` selects Postgres. */
    val dbUrl: String = "jdbc:sqlite:${File(dataDir, "klean.sqlite").absolutePath}",
    val dbUser: String? = null,
    val dbPassword: String? = null,
    /** Plain admin password from the environment; hashed with argon2id into `settings` at start. */
    val adminPassword: String? = null,
    /** The URL phones use to reach this server; goes into the pairing QR. */
    val publicUrl: String = "http://localhost:$port",
    val blobMaxBytes: Long = 50L * 1024 * 1024,
    val pairCodeTtlMs: Long = 10 * 60 * 1000L,
    /** OpenAI-compatible base URL including `/v1`, e.g. `http://ollama:11434/v1`. */
    val aiUpstream: String? = null,
    val aiApiKey: String? = null,
    val hermesUrl: String? = null,
    val hermesToken: String? = null,
    val googleClientId: String? = null,
    val googleClientSecret: String? = null,
    /** Defaults to `<publicUrl>/api/v1/google/oauth/callback`. */
    val googleRedirectUri: String? = null,
    /** VAPID `sub` claim (a mailto: or https: URL). */
    val vapidSubject: String = "mailto:admin@klean.invalid",
) {
    val isSqlite: Boolean get() = dbUrl.startsWith("jdbc:sqlite:")
    val blobDir: File get() = File(dataDir, "blobs")
    val effectiveGoogleRedirectUri: String
        get() = googleRedirectUri ?: "${publicUrl.trimEnd('/')}/api/v1/google/oauth/callback"

    companion object {
        fun fromEnv(env: Map<String, String>): KleanConfig {
            fun get(key: String): String? = env[key]?.takeIf { it.isNotBlank() }
            val port = get("KLEAN_PORT")?.toInt() ?: 8080
            val dataDir = File(get("KLEAN_DATA_DIR") ?: "./data")
            val base = KleanConfig(port = port, dataDir = dataDir)
            return base.copy(
                host = get("KLEAN_HOST") ?: base.host,
                dbUrl = get("KLEAN_DB_URL") ?: base.dbUrl,
                dbUser = get("KLEAN_DB_USER"),
                dbPassword = get("KLEAN_DB_PASSWORD"),
                adminPassword = get("KLEAN_ADMIN_PASSWORD"),
                publicUrl = get("KLEAN_PUBLIC_URL") ?: base.publicUrl,
                blobMaxBytes = get("KLEAN_BLOB_MAX_MB")?.toLong()?.times(1024 * 1024) ?: base.blobMaxBytes,
                aiUpstream = get("KLEAN_AI_UPSTREAM"),
                aiApiKey = get("KLEAN_AI_API_KEY"),
                hermesUrl = get("KLEAN_HERMES_URL"),
                hermesToken = get("KLEAN_HERMES_TOKEN"),
                googleClientId = get("KLEAN_GOOGLE_CLIENT_ID"),
                googleClientSecret = get("KLEAN_GOOGLE_CLIENT_SECRET"),
                googleRedirectUri = get("KLEAN_GOOGLE_REDIRECT_URI"),
                vapidSubject = get("KLEAN_VAPID_SUBJECT") ?: base.vapidSubject,
            )
        }
    }
}
