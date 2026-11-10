package app.klean.core.google

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class GTaskList(val id: String, val title: String = "", val etag: String? = null, val updated: String? = null)

@Serializable
data class GTask(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    /** "needsAction" or "completed". */
    val status: String = "needsAction",
    /** RFC 3339; only the date part means anything (Google Tasks has no due times in the API). */
    val due: String? = null,
    /** RFC 3339 completion time. */
    val completed: String? = null,
    val parent: String? = null,
    val position: String? = null,
    val etag: String? = null,
    val deleted: Boolean = false,
    val hidden: Boolean = false,
    val updated: String? = null,
) {
    val isDone get() = status == STATUS_DONE

    companion object {
        const val STATUS_OPEN = "needsAction"
        const val STATUS_DONE = "completed"
    }
}

@Serializable internal data class GListsPage(val items: List<GTaskList> = emptyList(), val nextPageToken: String? = null)
@Serializable internal data class GTasksPage(val items: List<GTask> = emptyList(), val nextPageToken: String? = null)

/**
 * The fields Klean may change on an existing Google task: title, notes, due **date**, done.
 * [due] is yyyy-MM-dd or null (clears the date).
 */
data class GTaskPatch(val title: String, val notes: String?, val due: String?, val done: Boolean, val completedAt: String? = null)

/** Not connected, or Google wants consent again: the user has to reconnect in Settings. */
class GoogleNotConnectedException : IOException("Google Tasks isn't connected")

class GoogleHttpException(val status: Int, message: String) : IOException("HTTP $status: $message")

/**
 * The Google Tasks API surface Klean uses. Deliberately has no way to create (POST) or delete a task:
 * Klean shows Google tasks and edits a few fields, it never adds to the user's Google lists.
 */
interface GoogleTasksApi {
    suspend fun lists(): List<GTaskList>
    /** Every task in the list, completed and hidden ones included, across pages. */
    suspend fun tasks(listId: String): List<GTask>
    /** `PATCH lists/{listId}/tasks/{taskId}`: complete, uncomplete, title, notes, due date. */
    suspend fun patch(listId: String, taskId: String, patch: GTaskPatch): GTask
}

private val GJson = Json { ignoreUnknownKeys = true }

class GoogleTasksClient(
    private val client: HttpClient,
    private val auth: GoogleAuth,
    baseUrl: String = BASE_URL,
) : GoogleTasksApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun lists(): List<GTaskList> {
        val out = mutableListOf<GTaskList>()
        var page: String? = null
        do {
            val r = authed { client.get("$base/users/@me/lists") { it(this); parameter("maxResults", 100); page?.let { p -> parameter("pageToken", p) } } }
            val body = GJson.decodeFromString(GListsPage.serializer(), r)
            out += body.items
            page = body.nextPageToken
        } while (page != null)
        return out
    }

    override suspend fun tasks(listId: String): List<GTask> {
        val out = mutableListOf<GTask>()
        var page: String? = null
        do {
            val r = authed {
                client.get("$base/lists/${seg(listId)}/tasks") {
                    it(this)
                    parameter("maxResults", 100)
                    parameter("showCompleted", true)
                    parameter("showHidden", true)
                    page?.let { p -> parameter("pageToken", p) }
                }
            }
            val body = GJson.decodeFromString(GTasksPage.serializer(), r)
            out += body.items
            page = body.nextPageToken
        } while (page != null)
        return out
    }

    override suspend fun patch(listId: String, taskId: String, patch: GTaskPatch): GTask {
        val body = buildJsonObject {
            put("title", JsonPrimitive(patch.title))
            put("notes", patch.notes?.let(::JsonPrimitive) ?: JsonNull)
            put("due", patch.due?.let { JsonPrimitive("${it}T00:00:00.000Z") } ?: JsonNull)
            put("status", JsonPrimitive(if (patch.done) GTask.STATUS_DONE else GTask.STATUS_OPEN))
            // Uncompleting needs the completion time cleared too.
            put("completed", if (patch.done) patch.completedAt?.let(::JsonPrimitive) ?: JsonNull else JsonNull)
        }
        val r = authed {
            client.patch("$base/lists/${seg(listId)}/tasks/${seg(taskId)}") {
                it(this)
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        }
        return GJson.decodeFromString(GTask.serializer(), r)
    }

    /** Runs [call] with a bearer token; on 401 drops the token and tries once more with a fresh one. */
    private suspend fun authed(call: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse): String {
        val token = auth.accessToken() ?: throw GoogleNotConnectedException()
        var r = call { bearerAuth(token) }
        if (r.status.value == 401) {
            auth.invalidate(token)
            val fresh = auth.accessToken() ?: throw GoogleNotConnectedException()
            r = call { bearerAuth(fresh) }
            if (r.status.value == 401) throw GoogleNotConnectedException()
        }
        if (!r.status.isSuccess()) throw GoogleHttpException(r.status.value, r.bodyAsText().take(200))
        return r.bodyAsText()
    }

    private fun seg(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        const val BASE_URL = "https://tasks.googleapis.com/tasks/v1"
    }
}
