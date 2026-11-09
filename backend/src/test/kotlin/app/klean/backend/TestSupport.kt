package app.klean.backend

import app.klean.backend.auth.LoginRequest
import app.klean.backend.auth.PairClaimRequest
import app.klean.backend.auth.PairClaimResponse
import app.klean.backend.auth.PairStartResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
    block: suspend ApplicationTestBuilder.(Services) -> Unit,
) = testApplication {
    val s = services(config)
    application { kleanModule(s) }
    try {
        block(s)
    } finally {
        s.close()
    }
}

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(KleanJson) }
}

suspend fun HttpClient.login(): PairClaimResponse = post("/api/v1/auth/login") {
    contentType(ContentType.Application.Json)
    setBody(LoginRequest(TEST_ADMIN_PASSWORD, "web"))
}.body()

suspend fun HttpClient.claimDevice(adminToken: String, name: String, platform: String = "ANDROID"): PairClaimResponse {
    val start = post("/api/v1/pair/start") { bearerAuth(adminToken) }.body<PairStartResponse>()
    return post("/api/v1/pair/claim") {
        contentType(ContentType.Application.Json)
        setBody(PairClaimRequest(start.code, name, platform))
    }.body()
}
