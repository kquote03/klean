package app.klean.core.sync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.backend.KleanConfig
import app.klean.backend.Services
import app.klean.backend.auth.LoginRequest
import app.klean.backend.auth.PairClaimResponse
import app.klean.backend.auth.PairStartResponse
import app.klean.backend.kleanModule
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.FoodEntryEntity
import app.klean.core.data.db.entity.SyncColumns
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.db.transact
import app.klean.shared.diet.CaptureSource
import app.klean.shared.diet.EnrichmentStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers

fun tempDir(prefix: String): File = Files.createTempDirectory(prefix).toFile()

fun inMemoryDb(): KleanDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** One simulated phone: its own database, files dir and device id. */
class Phone(val id: String) {
    val db = inMemoryDb()
    val files = tempDir("phone-$id")
    val blobs = BlobFiles(files)
    val engine = SyncEngine(RoomLocalStore(db), blobs, { id })

    /** A write as a repository makes it: next `change_seq`, inside a transaction. */
    suspend fun stamp(at: Long, previous: SyncColumns? = null, deleted: Boolean = false): SyncColumns =
        SyncColumns(
            previous?.createdAt ?: at,
            if (previous != null && previous.deviceId != id) maxOf(at, previous.updatedAt + 1) else at, // as SyncStamper
            id, deleted, db.changeCounterDao().next(),
        )

    suspend fun putTask(taskId: String, title: String, at: Long, deleted: Boolean = false) = db.transact {
        val prev = db.taskDao().get(taskId)
        db.taskDao().upsert(
            (prev ?: TaskEntity(id = taskId, listId = "inbox", title = title, sync = SyncColumns(at, at, id, false, 0)))
                .copy(title = title, sync = stamp(at, prev?.sync, deleted)),
        )
    }

    suspend fun task(taskId: String) = db.taskDao().get(taskId)

    suspend fun putFood(entryId: String, text: String, at: Long, photo: String? = null) = db.transact {
        db.foodEntryDao().upsert(
            FoodEntryEntity(
                id = entryId, rawText = text, eatenAt = at, eatenAtZone = "Asia/Dubai", photoFile = photo,
                source = CaptureSource.DASHBOARD, viaVoice = false, mealType = null, mealTypeProvider = null,
                enrichment = EnrichmentStatus.NOT_REQUESTED, sync = stamp(at),
            ),
        )
    }

    fun close() = db.close()
}

/** An in-process backend (the real `:backend` module) and helpers to pair phones with it. */
class Server(private val builder: ApplicationTestBuilder) {
    val client: HttpClient = builder.createClient { expectSuccess = false }
    lateinit var admin: String

    suspend fun login() {
        val r = client.post("/api/v1/auth/login") { setBody(TextContent(SyncJson.encodeToString(LoginRequest.serializer(), LoginRequest(PASSWORD)), ContentType.Application.Json)) }
        admin = SyncJson.decodeFromString(PairClaimResponse.serializer(), r.bodyAsText()).token
    }

    suspend fun pairCode(): PairStartResponse {
        val r = client.post("/api/v1/pair/start") { bearerAuth(admin) }
        return SyncJson.decodeFromString(PairStartResponse.serializer(), r.bodyAsText())
    }

    /** Pairs [phone] the way the app does (claim with its own device id) and returns its API. */
    suspend fun pair(phone: Phone): KtorSyncApi {
        val link = PairLink.parse(pairCode().qr)!!
        val claimed = claimPairing(client, "", PairClaimBody(link.code, "Phone ${phone.id}", "ANDROID", phone.id))
        check(claimed.deviceId == phone.id)
        return KtorSyncApi(client, "", claimed.token)
    }

    companion object {
        const val PASSWORD = "correct horse battery staple"
    }
}

fun withServer(block: suspend Server.() -> Unit) = testApplication {
    val dir = tempDir("klean-backend")
    val services = Services.create(KleanConfig(dataDir = dir, adminPassword = Server.PASSWORD, publicUrl = "http://klean.test"))
    application { kleanModule(services) }
    try {
        Server(this).apply { login() }.block()
    } finally {
        services.close()
    }
}
