package app.klean.feature.delegation.agent

import kotlinx.coroutines.flow.Flow

/**
 * What an agent gets about a task. Deliberately no credentials, tokens or account fields: agents act with
 * their own tools, Klean never passes the user's mail or calendar access along (`DelegationPayloadTest`).
 */
data class DelegationPayload(
    val delegationId: String,
    val title: String,
    val notes: String?,
    /** "2026-11-12", or null. */
    val due: String?,
    /** PARTIAL, INTERACTIVE or AGENTIC. */
    val mode: String,
)

/** Something the agent reports. */
sealed interface AgentEvent {
    /** The agent asks the user something; [requestId] identifies a native Hermes clarify prompt, if any. */
    data class Question(val text: String, val requestId: String? = null) : AgentEvent
    /** An intermediate draft to show on the task. */
    data class Draft(val text: String) : AgentEvent
    /** The agent wants to act (e.g. send an email) and waits for a decision. */
    data class ApprovalNeeded(
        val action: String,
        val draft: String,
        val recipients: List<String>,
        val requestId: String? = null,
    ) : AgentEvent
    /** Done. [subtasks] only from the partial (AI) agent. */
    data class Result(val text: String, val subtasks: List<String> = emptyList()) : AgentEvent
    data class Failed(val reason: String) : AgentEvent
}

/** The user's decision on an approval: APPROVED, EDITED (with the edited draft and recipients) or REJECTED. */
data class ApprovalDecision(val decision: String, val draft: String, val recipients: List<String>, val requestId: String? = null) {
    companion object {
        const val APPROVED = "APPROVED"
        const val EDITED = "EDITED"
        const val REJECTED = "REJECTED"
    }
}

/** An agent a task can be delegated to (plan M23). */
interface DelegationAgent {
    /** Starts work and returns the agent's reference for it (stored as `delegation.remote_ref`). */
    suspend fun start(task: DelegationPayload): String
    /** Events until a [AgentEvent.Result] or [AgentEvent.Failed]; waits across questions and approvals. */
    fun events(remoteRef: String): Flow<AgentEvent>
    suspend fun answer(remoteRef: String, text: String)
    suspend fun decide(remoteRef: String, approval: ApprovalDecision)
}
