package app.klean.feature.delegation.hermes

import app.klean.core.data.secret.SecretStore
import app.klean.feature.delegation.agent.HermesConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The user's Hermes: URL and token, both kept in [SecretStore] on this phone only (`hermes.url`,
 * `hermes.token`); never synced, backed up only if the user opts in to secrets in backups.
 */
@Singleton
class HermesSettings @Inject constructor(
    private val secrets: SecretStore,
    @Named(HTTP) private val client: HttpClient,
) : HermesConfig {
    enum class TestResult { OK, UNAUTHORIZED, UNREACHABLE, NOT_SET }

    override suspend fun url(): String? = secrets.get(URL_KEY)?.takeIf { it.isNotBlank() }
    override suspend fun token(): String? = secrets.get(SecretStore.HERMES_TOKEN)?.takeIf { it.isNotBlank() }

    suspend fun configured(): Boolean = url() != null

    suspend fun save(url: String, token: String) {
        secrets.put(URL_KEY, url.trim().trimEnd('/').ifEmpty { null })
        secrets.put(SecretStore.HERMES_TOKEN, token.trim().ifEmpty { null })
    }

    /** `GET /v1/health` with the token; calm categories for the settings screen. */
    suspend fun test(): TestResult {
        val base = url() ?: return TestResult.NOT_SET
        return try {
            val r = client.get("$base/v1/health") { header(HttpHeaders.Authorization, "Bearer ${token().orEmpty()}") }
            when (r.status.value) {
                in 200..299 -> TestResult.OK
                401, 403 -> TestResult.UNAUTHORIZED
                else -> TestResult.UNREACHABLE
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            TestResult.UNREACHABLE
        }
    }

    companion object {
        const val URL_KEY = "hermes.url"
        const val HTTP = "delegation_http"
    }
}
