package app.klean.backend

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files

/** A fresh data dir (and SQLite file) per test, under build/tmp so nothing leaks into the repo. */
fun tempDataDir(): File {
    val root = File(System.getProperty("klean.test.tmp") ?: System.getProperty("java.io.tmpdir")).apply { mkdirs() }
    return Files.createTempDirectory(root.toPath(), "klean-").toFile()
}

fun testConfig(dataDir: File = tempDataDir()) = KleanConfig(
    dataDir = dataDir,
    adminPassword = TEST_ADMIN_PASSWORD,
    publicUrl = "http://klean.test",
)

const val TEST_ADMIN_PASSWORD = "correct horse battery staple"

fun kleanTest(
    config: KleanConfig = testConfig(),
    services: (KleanConfig) -> Services = { Services.create(it) },
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    val s = services(config)
    application { kleanModule(s) }
    block()
}
