package app.klean.feature.delegation.agent

import app.klean.core.ai.AiOutcome
import app.klean.core.ai.AiService
import app.klean.core.ai.StructuredRequest
import app.klean.shared.ai.AiTask
import app.klean.shared.delegation.PARTIAL_DELEGATION_SCHEMA
import app.klean.shared.delegation.PARTIAL_DELEGATION_SYSTEM
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Partial delegation (plan M23): one structured AI call through the user's own provider (the AI task
 * \"Delegation\") that drafts the work, proposes subtasks and notes. Nothing is sent anywhere; the user reads
 * the draft and accepts the subtasks if they want them.
 */
class PartialAgent @Inject constructor(private val ai: AiService) : DelegationAgent {
    private val pending = ConcurrentHashMap<String, DelegationPayload>()

    override suspend fun start(task: DelegationPayload): String {
        val ref = "ai-${task.delegationId}"
        pending[ref] = task
        return ref
    }

    override fun events(remoteRef: String): Flow<AgentEvent> = flow {
        val task = pending.remove(remoteRef) ?: run { emit(AgentEvent.Failed("not started")); return@flow }
        val user = buildString {
            append("Task: ").append(task.title)
            task.notes?.takeIf { it.isNotBlank() }?.let { append("\nNotes: ").append(it) }
            task.due?.let { append("\nDue: ").append(it) }
        }
        when (val out = ai.run(AiTask.DELEGATION, StructuredRequest(PARTIAL_DELEGATION_SYSTEM, user, PARTIAL_DELEGATION_SCHEMA))) {
            is AiOutcome.Success -> {
                val o = out.json as? JsonObject
                val draft = (o?.get("draft") as? JsonPrimitive)?.content.orEmpty()
                val notes = (o?.get("notes") as? JsonPrimitive)?.content.orEmpty()
                val subtasks = (o?.get("subtasks") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
                emit(AgentEvent.Result(listOf(draft, notes).filter { it.isNotBlank() }.joinToString("\n\n"), subtasks.take(MAX_SUBTASKS)))
            }
            AiOutcome.NoProvider -> emit(AgentEvent.Failed(NO_PROVIDER))
            is AiOutcome.Unavailable -> emit(AgentEvent.Failed(out.reason))
            is AiOutcome.Failed -> emit(AgentEvent.Failed(out.reason))
        }
    }

    override suspend fun answer(remoteRef: String, text: String) {}
    override suspend fun decide(remoteRef: String, approval: ApprovalDecision) {}

    companion object {
        const val MAX_SUBTASKS = 12
        const val NO_PROVIDER = "no-provider"
    }
}
