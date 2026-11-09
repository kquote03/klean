package app.klean.backend

/** The server's object graph, built once at start (or per test with fakes). */
class Services(
    val config: KleanConfig,
) {
    companion object {
        fun create(config: KleanConfig): Services {
            config.dataDir.mkdirs()
            return Services(config)
        }
    }
}
