package app.klean.feature.delegation.agent

import app.klean.shared.delegation.KleanBlock
import app.klean.shared.delegation.KleanBlocks
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Where Hermes is: its API Server (or the Klean backend's relay) and the bearer token. */
interface HermesConfig {
    suspend fun url(): String?
    suspend fun token(): String?
}

class HermesUnavailable(message: String) : Exception(message)

/**
 * [DelegationAgent] over the Hermes API Server's Runs API (`docs/hermes.md`). One Hermes session per
 * delegation (`klean-<id>`, the remote ref); each step is a run in it. Questions and approvals come either as
 * Hermes' own `clarify.request` / `approval.request` events (answered on the same run) or as Klean blocks in
 * the assistant's text (answered by starting the next run with a Klean block).
 */
class HermesAgent(
    private val client: HttpClient,
    private val config: HermesConfig,
) : DelegationAgent {
    private class Session {
        val run = MutableStateFlow<String?>(null)
        @Volatile var clarify: Pair<String, Int?>? = null
        @Volatile var approval: String? = null
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private fun session(ref: String) = sessions.getOrPut(ref) { Session() }

    override suspend fun start(task: DelegationPayload): String {
        val ref = "klean-${task.delegationId}"
        val text = buildString {
            append(task.title)
            task.notes?.takeIf { it.isNotBlank() }?.let { append("\n\nNotes: ").append(it) }
            task.due?.let { append("\nDue: ").append(it) }
            append("\nMode: ").append(task.mode)
        }
        startRun(ref, text, withInstructions = true)
        return ref
    }

    override fun events(remoteRef: String): Flow<AgentEvent> = channelFlow {
        val s = session(remoteRef)
        var streamed: String? = null
        while (true) {
            val run = s.run.first { it != null && it != streamed }!!
            streamed = run
            val text = StringBuilder()
            var finished: AgentEvent? = null
            var waiting = false
            val (base, token) = endpoint()
            client.prepareGet("$base/v1/runs/$run/events") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "text/event-stream")
            }.execute { resp ->
                if (!resp.status.isSuccess()) {
                    finished = AgentEvent.Failed("events ${resp.status.value}")
                    return@execute
                }
                val ch = resp.bodyAsChannel()
                while (finished == null && !waiting) {
                    val line = ch.readUTF8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val obj = runCatching { json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject }.getOrNull() ?: continue
                    when (obj.str("event")) {
                        "message.delta" -> obj.str("delta")?.let(text::append)
                        "clarify.request" -> {
                            val id = obj.str("request_id") ?: obj.str("prompt_id")
                            s.clarify = id?.let { it to (obj["execution_generation"] ?: obj["generation"])?.jsonPrimitive?.intOrNull }
                            send(AgentEvent.Question(obj.str("question") ?: obj.str("text") ?: obj.str("prompt").orEmpty(), id))
                        }
                        "approval.request" -> {
                            val id = obj.str("request_id") ?: obj.str("prompt_id")
                            s.approval = id
                            send(
                                AgentEvent.ApprovalNeeded(
                                    action = obj.str("tool_name") ?: obj.str("action") ?: "tool",
                                    draft = obj.str("description") ?: obj.str("command") ?: obj.str("preview").orEmpty(),
                                    recipients = emptyList(),
                                    requestId = id,
                                ),
                            )
                        }
                        "run.completed" -> {
                            val all = text.toString().ifEmpty { obj.str("output").orEmpty() }
                            val blocks = KleanBlocks.parse(all)
                            blocks.filterIsInstance<KleanBlock.Draft>().forEach { send(AgentEvent.Draft(it.text)) }
                            val ask = blocks.firstOrNull { it is KleanBlock.Question || it is KleanBlock.Approval }
                            when (ask) {
                                is KleanBlock.Question -> { send(AgentEvent.Question(ask.text)); waiting = true }
                                is KleanBlock.Approval -> { send(AgentEvent.ApprovalNeeded(ask.action, ask.draft, ask.recipients)); waiting = true }
                                else -> finished = AgentEvent.Result(KleanBlocks.strip(all))
                            }
                        }
                        "run.failed", "run.cancelled", "run.interrupted" ->
                            finished = AgentEvent.Failed(obj.str("error") ?: obj.str("event").orEmpty())
                    }
                }
            }
            val done = finished
            if (done != null) {
                // The session is kept: a native prompt sent just before the end may still be answered.
                send(done)
                return@channelFlow
            }
            if (!waiting) {
                send(AgentEvent.Failed("stream ended"))
                return@channelFlow
            }
        }
    }

    override suspend fun answer(remoteRef: String, text: String) {
        val s = session(remoteRef)
        val pending = s.clarify
        val run = s.run.value
        if (pending != null && run != null) {
            s.clarify = null
            post("/v1/runs/$run/clarify", buildJsonObject {
                put("request_id", pending.first)
                pending.second?.let { put("execution_generation", it) }
                put("answer", text)
            })
        } else {
            startRun(remoteRef, KleanBlocks.answer(text), withInstructions = false)
        }
    }

    override suspend fun decide(remoteRef: String, approval: ApprovalDecision) {
        val s = session(remoteRef)
        val pending = s.approval
        val run = s.run.value
        if (pending != null && run != null) {
            s.approval = null
            post("/v1/runs/$run/approval", buildJsonObject {
                put("choice", if (approval.decision == ApprovalDecision.REJECTED) "deny" else "once")
                put("request_id", pending)
            })
        } else {
            startRun(remoteRef, KleanBlocks.decision(approval.decision, approval.draft, approval.recipients), withInstructions = false)
        }
    }

    private suspend fun startRun(ref: String, input: String, withInstructions: Boolean) {
        val body = buildJsonObject {
            put("input", input)
            put("session_id", ref)
            if (withInstructions) put("instructions", KleanBlocks.INSTRUCTIONS)
        }
        val resp = post("/v1/runs", body)
        val runId = json.parseToJsonElement(resp).jsonObject.str("run_id") ?: throw HermesUnavailable("no run id")
        session(ref).run.value = runId
    }

    private suspend fun post(path: String, body: JsonObject): String {
        val (base, token) = endpoint()
        val resp = client.post("$base$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (!resp.status.isSuccess()) throw HermesUnavailable("$path ${resp.status.value}")
        return resp.bodyAsText()
    }

    private suspend fun endpoint(): Pair<String, String> {
        val url = config.url()?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: throw HermesUnavailable("no url")
        return url to config.token().orEmpty()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
