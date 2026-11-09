package app.klean.backend.google

import app.klean.backend.ApiException
import app.klean.backend.KleanConfig
import app.klean.backend.KleanJson
import app.klean.backend.b64Url
import app.klean.backend.db.GoogleTokensTable
import app.klean.backend.db.KleanDb
import app.klean.backend.db.SettingsTable
import app.klean.backend.randomBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import kotlin.time.Clock

@Serializable data class GoogleStartResponse(val url: String)
@Serializable data class GoogleStatus(val configured: Boolean, val connected: Boolean)

/** An upstream answer passed through as-is (status + JSON body). */
data class Upstream(val status: HttpStatusCode, val body: String)

/**
 * Server-side Google for the web client: OAuth (web client type, offline access), Calendar events read-only, and
 * Google Tasks list + patch. **It never creates Google tasks**: there is no code path that POSTs to the Tasks
 * API (asserted by `GoogleTest.neverCreatesGoogleTasks`). Patches carry only title, notes, due (a date) and status.
 */
class GoogleService(
    private val db: KleanDb,
    private val config: KleanConfig,
    private val http: HttpClient,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(GoogleService::class.java)

    @Volatile private var access: Pair<String, Long>? = null

    val configured: Boolean get() = config.googleClientId != null && config.googleClientSecret != null

    suspend fun status() = GoogleStatus(configured, refreshToken() != null)

    suspend fun startUrl(): GoogleStartResponse {
        if (!configured) throw ApiException(HttpStatusCode.ServiceUnavailable, "Google is not configured on this server")
        val state = randomBytes(24).b64Url()
        val expires = clock.now().toEpochMilliseconds() + STATE_TTL_MS
        db.putSetting(STATE_KEY, "$state $expires")
        val url = URLBuilder(AUTH_URL).apply {
            parameters.append("client_id", config.googleClientId!!)
            parameters.append("redirect_uri", config.effectiveGoogleRedirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", SCOPES.joinToString(" "))
            parameters.append("access_type", "offline")
            parameters.append("prompt", "consent")
            parameters.append("include_granted_scopes", "true")
            parameters.append("state", state)
        }.buildString()
        return GoogleStartResponse(url)
    }

    suspend fun callback(code: String?, state: String?) {
        if (!configured) throw ApiException(HttpStatusCode.ServiceUnavailable, "Google is not configured on this server")
        val stored = db.setting(STATE_KEY)?.split(' ')
        val valid = stored != null && stored.size == 2 && state != null && stored[0] == state &&
            (stored[1].toLongOrNull() ?: 0) > clock.now().toEpochMilliseconds()
        if (!valid || code.isNullOrBlank()) throw ApiException(HttpStatusCode.BadRequest, "invalid OAuth state")
        db.write { SettingsTable.deleteWhere { SettingsTable.key eq STATE_KEY } }
        val response = http.post(TOKEN_URL) {
            setBody(FormDataContent(parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", config.googleClientId!!)
                append("client_secret", config.googleClientSecret!!)
                append("redirect_uri", config.effectiveGoogleRedirectUri)
            }))
        }
        val json = parseObject(response)
        if (!response.status.isSuccess()) throw ApiException(HttpStatusCode.BadGateway, "Google refused the code")
        val refresh = json["refresh_token"]?.jsonPrimitive?.contentOrNull
            ?: throw ApiException(HttpStatusCode.BadGateway, "Google sent no refresh token")
        cacheAccess(json)
        db.write {
            GoogleTokensTable.upsert {
                it[id] = ACCOUNT
                it[refreshToken] = refresh
                it[scope] = json["scope"]?.jsonPrimitive?.contentOrNull.orEmpty()
                it[updatedAt] = clock.now().toEpochMilliseconds()
            }
        }
        log.info("Google connected")
    }

    suspend fun disconnect() {
        access = null
        db.write { GoogleTokensTable.deleteWhere { GoogleTokensTable.id eq ACCOUNT } }
    }

    suspend fun calendarEvents(calendarId: String, timeMin: String?, timeMax: String?, pageToken: String?): Upstream =
        authorized { token ->
            http.get("$CALENDAR_BASE/calendars/${seg(calendarId)}/events") {
                header(HttpHeaders.Authorization, "Bearer $token")
                timeMin?.let { parameter("timeMin", it) }
                timeMax?.let { parameter("timeMax", it) }
                pageToken?.let { parameter("pageToken", it) }
                parameter("singleEvents", "true")
                parameter("orderBy", "startTime")
                parameter("maxResults", "250")
            }
        }

    suspend fun taskLists(): Upstream = authorized { token ->
        http.get("$TASKS_BASE/users/@me/lists") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("maxResults", "100")
        }
    }

    suspend fun tasks(listId: String, pageToken: String?): Upstream = authorized { token ->
        http.get("$TASKS_BASE/lists/${seg(listId)}/tasks") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("showCompleted", "true")
            parameter("showHidden", "true")
            parameter("maxResults", "100")
            pageToken?.let { parameter("pageToken", it) }
        }
    }

    /** Complete/uncomplete or edit title, notes and due date. Any other field is dropped. */
    suspend fun patchTask(listId: String, taskId: String, patch: JsonObject): Upstream {
        val body = sanitisePatch(patch)
        return authorized { token ->
            http.patch("$TASKS_BASE/lists/${seg(listId)}/tasks/${seg(taskId)}") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(TextContent(KleanJson.encodeToString(JsonObject.serializer(), body), ContentType.Application.Json))
            }
        }
    }

    internal fun expireAccessTokenForTest() {
        access = access?.copy(second = 0)
    }

    private suspend fun authorized(call: suspend (String) -> HttpResponse): Upstream {
        var response = call(accessToken())
        if (response.status == HttpStatusCode.Unauthorized) {
            access = null
            response = call(accessToken())
        }
        return Upstream(response.status, response.bodyAsText())
    }

    private suspend fun accessToken(): String {
        access?.let { (token, exp) -> if (exp > clock.now().toEpochMilliseconds() + 60_000) return token }
        val refresh = refreshToken() ?: throw ApiException(HttpStatusCode.Conflict, "Google is not connected")
        val response = http.post(TOKEN_URL) {
            setBody(FormDataContent(parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refresh)
                append("client_id", config.googleClientId.orEmpty())
                append("client_secret", config.googleClientSecret.orEmpty())
            }))
        }
        val json = parseObject(response)
        if (!response.status.isSuccess()) {
            log.warn("Google token refresh failed status={}", response.status.value)
            throw ApiException(HttpStatusCode.BadGateway, "Google token refresh failed; reconnect Google")
        }
        return cacheAccess(json)
    }

    private fun cacheAccess(json: JsonObject): String {
        val token = json["access_token"]?.jsonPrimitive?.contentOrNull ?: throw ApiException(HttpStatusCode.BadGateway, "no access token")
        val expiresIn = json["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600
        access = token to clock.now().toEpochMilliseconds() + expiresIn * 1000
        return token
    }

    private suspend fun refreshToken(): String? = db.read {
        GoogleTokensTable.selectAll().where { GoogleTokensTable.id eq ACCOUNT }.singleOrNull()?.get(GoogleTokensTable.refreshToken)
    }

    private suspend fun parseObject(r: HttpResponse): JsonObject =
        runCatching { KleanJson.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    private fun HttpStatusCode.isSuccess() = value in 200..299

    companion object {
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val CALENDAR_BASE = "https://www.googleapis.com/calendar/v3"
        const val TASKS_BASE = "https://tasks.googleapis.com/tasks/v1"
        val SCOPES = listOf("https://www.googleapis.com/auth/tasks", "https://www.googleapis.com/auth/calendar.readonly")
        private const val STATE_KEY = "google.oauthState"
        private const val STATE_TTL_MS = 10 * 60_000L
        private const val ACCOUNT = "default"
        private val ALLOWED = setOf("title", "notes", "due", "status")
        private val SEGMENT = Regex("^[A-Za-z0-9_@.%=-]{1,200}$")

        private fun seg(s: String): String {
            if (!SEGMENT.matches(s)) throw ApiException(HttpStatusCode.BadRequest, "bad id")
            return s
        }

        fun sanitisePatch(patch: JsonObject): JsonObject {
            val out = LinkedHashMap<String, JsonElement>()
            for ((k, v) in patch) {
                if (k !in ALLOWED) continue
                when (k) {
                    "status" -> {
                        val s = (v as? JsonPrimitive)?.contentOrNull
                        if (s != "needsAction" && s != "completed") throw ApiException(HttpStatusCode.BadRequest, "status must be needsAction or completed")
                        out[k] = v
                        if (s == "needsAction") out["completed"] = JsonNull
                    }
                    "due" -> out[k] = when {
                        v is JsonNull -> JsonNull
                        else -> {
                            val d = (v as? JsonPrimitive)?.contentOrNull?.take(10)
                            if (d == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(d)) throw ApiException(HttpStatusCode.BadRequest, "due must be a date")
                            // The Tasks API keeps only the date part of `due`.
                            JsonPrimitive("${d}T00:00:00.000Z")
                        }
                    }
                    else -> out[k] = v
                }
            }
            if (out.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "nothing to change")
            return JsonObject(out)
        }
    }
}
