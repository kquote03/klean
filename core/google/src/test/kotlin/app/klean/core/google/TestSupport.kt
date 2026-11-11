package app.klean.core.google

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FakeClock(var nowMs: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}

fun inMemoryDb(): KleanDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

fun tempScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun tempDevicePrefs(clock: Clock, scope: CoroutineScope): DevicePrefs {
    val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
    return DevicePrefs(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock)
}

fun tempGoogleState(scope: CoroutineScope): GoogleLocalState {
    val file = File(createTempDirectory("google").toFile(), "google.preferences_pb")
    return GoogleLocalState(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
}

/**
 * Every request any Google test makes goes through here, so [NoTaskCreationTest] and the per-request guard
 * see the whole suite's client usage. A POST is answered with an assertion failure, never a success.
 */
object GoogleRequestLog {
    val all: MutableList<Pair<HttpMethod, String>> = java.util.Collections.synchronizedList(mutableListOf())
}

/** A tiny in-memory Google Tasks server for MockEngine: lists, tasks (paged by 2), PATCH. */
class FakeGoogleServer {
    data class Item(var task: GTask, val listId: String)

    val lists = mutableListOf<GTaskList>()
    val items = mutableListOf<Item>()
    val requests = mutableListOf<HttpRequestData>()
    val patches = mutableListOf<Pair<String, JsonObject>>()
    /** The bearer token each PATCH carried, in order (cross-account checks). */
    val patchTokens = mutableListOf<String?>()
    var offline = false
    var failStatus: Int? = null
    /** Tokens the server accepts. */
    var validTokens = setOf("fake-token")
    private var etag = 0

    fun list(id: String, title: String) = lists.add(GTaskList(id, title))
    fun task(listId: String, t: GTask) = items.add(Item(t.copy(etag = t.etag ?: "e${etag++}"), listId))

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    val engine = MockEngine { req ->
        requests += req
        GoogleRequestLog.all += req.method to req.url.encodedPath
        check(req.method != HttpMethod.Post) { "Klean must never POST to Google Tasks: ${req.url}" }
        if (offline) throw IOException("offline")
        failStatus?.let { return@MockEngine respond("", HttpStatusCode.fromValue(it)) }
        val auth = req.headers["Authorization"]?.removePrefix("Bearer ")
        if (auth !in validTokens) return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
        val path = req.url.encodedPath.removePrefix("/tasks/v1")
        val headers = headersOf("Content-Type", "application/json")
        val listTasks = Regex("^/lists/([^/]+)/tasks$").find(path)
        val oneTask = Regex("^/lists/([^/]+)/tasks/([^/]+)$").find(path)
        when {
            req.method == HttpMethod.Get && path == "/users/@me/lists" ->
                respond(json.encodeToString(GListsPage.serializer(), GListsPage(lists.toList())), HttpStatusCode.OK, headers)
            req.method == HttpMethod.Get && listTasks != null -> {
                val l = listTasks.groupValues[1]
                val all = items.filter { it.listId == l }.map { it.task }
                val start = req.url.parameters["pageToken"]?.toInt() ?: 0
                val page = all.drop(start).take(2)
                val next = (start + 2).takeIf { it < all.size }?.toString()
                respond(json.encodeToString(GTasksPage.serializer(), GTasksPage(page, next)), HttpStatusCode.OK, headers)
            }
            req.method == HttpMethod.Patch && oneTask != null -> {
                val item = items.firstOrNull { it.listId == oneTask.groupValues[1] && it.task.id == oneTask.groupValues[2] }
                    ?: return@MockEngine respond("{}", HttpStatusCode.NotFound, headers)
                val body = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
                patches += item.task.id to body
                patchTokens += auth
                fun str(k: String) = body[k]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                item.task = item.task.copy(
                    title = str("title") ?: item.task.title, notes = str("notes"), due = str("due"),
                    status = str("status") ?: item.task.status, completed = str("completed"), etag = "e${etag++}",
                )
                respond(json.encodeToString(GTask.serializer(), item.task), HttpStatusCode.OK, headers)
            }
            else -> respond("{}", HttpStatusCode.NotFound, headers)
        }
    }

    fun client(auth: GoogleAuth) = GoogleTasksClient(HttpClient(engine), auth)
}

/**
 * A fake OpenID `userinfo` endpoint and photo host: each token maps to a profile JSON body; tokens without one get
 * 401 (no profile scopes). Photos are served from [photos] by URL.
 */
class FakeProfileServer {
    val profiles = mutableMapOf<String, String>()
    val photos = mutableMapOf<String, ByteArray>()
    val requested = mutableListOf<String>()
    var offline = false

    fun profile(token: String, email: String, name: String? = null, given: String? = null, picture: String? = null) {
        profiles[token] = buildString {
            append("{\"sub\":\"1\",\"email\":\"$email\",\"email_verified\":true")
            name?.let { append(",\"name\":\"$it\"") }
            given?.let { append(",\"given_name\":\"$it\"") }
            picture?.let { append(",\"picture\":\"$it\"") }
            append("}")
        }
    }

    val engine = MockEngine { req ->
        requested += req.url.toString()
        GoogleRequestLog.all += req.method to req.url.encodedPath
        if (offline) throw IOException("offline")
        if (req.url.encodedPath.endsWith("/userinfo")) {
            val token = req.headers["Authorization"]?.removePrefix("Bearer ")
            val body = profiles[token] ?: return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
            return@MockEngine respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val bytes = photos[req.url.toString()] ?: return@MockEngine respond("", HttpStatusCode.NotFound)
        respond(bytes, HttpStatusCode.OK, headersOf("Content-Type", "image/png"))
    }

    fun client() = GoogleProfileClient(HttpClient(engine))
}

/** The 8-byte PNG signature plus padding: enough for [looksLikeImage]. */
val FAKE_PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)
