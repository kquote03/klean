package app.klean.feature.delegation

import app.klean.core.common.ApplicationScope
import app.klean.feature.delegation.agent.AgentEvent
import app.klean.feature.delegation.agent.ApprovalDecision
import app.klean.feature.delegation.agent.DelegationAgent
import app.klean.feature.delegation.agent.DelegationPayload
import app.klean.feature.delegation.data.DelegationMode
import app.klean.feature.delegation.data.DelegationRepository
import app.klean.feature.delegation.data.DelegationRole
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.tasks.data.TaskDraft
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.delegation.ApprovalPolicy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What the user is told, outside the app (notifications). */
interface DelegationNotices {
    fun question(delegationId: String, taskTitle: String, text: String) {}
    fun approval(approvalId: String, taskTitle: String, action: String, recipients: Int) {}
    fun finished(delegationId: String, taskTitle: String, ok: Boolean) {}
    fun clear(delegationId: String) {}
}

/**
 * Runs delegations (plan M23): starts the agent, follows its events and writes them down (messages,
 * approval requests, status, result), and passes the user's answers and decisions back. [ApprovalPolicy]
 * decides which approvals reach the user; more than 5 recipients always do.
 */
@Singleton
class DelegationRunner @Inject constructor(
    private val repo: DelegationRepository,
    private val tasks: TasksRepository,
    @Named(HERMES) private val hermes: DelegationAgent,
    @Named(PARTIAL) private val partial: DelegationAgent,
    private val notices: DelegationNotices,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    private fun agentFor(mode: String) = if (mode == DelegationMode.PARTIAL) partial else hermes

    /** Hands [taskId] to an agent and returns the delegation id at once; the work goes on in the background. */
    suspend fun delegate(taskId: String, mode: String, autoComplete: Boolean = false): String {
        val task = tasks.get(taskId) ?: error("no task $taskId")
        val agentName = if (mode == DelegationMode.PARTIAL) AGENT_AI else AGENT_HERMES
        val id = repo.create(taskId, mode, agentName, autoComplete)
        val agent = agentFor(mode)
        jobs[id] = scope.launch {
            try {
                val ref = agent.start(DelegationPayload(id, task.title, task.notes, task.dueDate, mode))
                repo.update(id, DelegationStatus.RUNNING, remoteRef = ref)
                follow(id, mode, task.title, agent, ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(id, task.title, e.message ?: e.javaClass.simpleName)
            }
        }
        return id
    }

    private suspend fun follow(id: String, mode: String, title: String, agent: DelegationAgent, ref: String) {
        agent.events(ref).collect { e ->
            when (e) {
                is AgentEvent.Question -> {
                    repo.addMessage(id, DelegationRole.AGENT, e.text)
                    repo.update(id, DelegationStatus.NEEDS_INPUT)
                    notices.question(id, title, e.text)
                }
                is AgentEvent.Draft -> repo.addMessage(id, DelegationRole.AGENT, e.text)
                is AgentEvent.ApprovalNeeded -> {
                    if (ApprovalPolicy.needsApproval(mode, e.action, e.recipients)) {
                        val approvalId = repo.addApproval(id, e.action, e.draft, e.recipients, e.requestId)
                        repo.update(id, DelegationStatus.NEEDS_APPROVAL)
                        notices.approval(approvalId, title, e.action, e.recipients.size)
                    } else {
                        agent.decide(ref, ApprovalDecision(ApprovalDecision.APPROVED, e.draft, e.recipients, e.requestId))
                    }
                }
                is AgentEvent.Result -> {
                    if (e.subtasks.isNotEmpty()) repo.addMessage(id, DelegationRole.AGENT, e.subtasks.joinToString("\n") { "• $it" })
                    // Auto-complete first, so DONE is only seen once everything it implies has happened.
                    val d = repo.get(id)
                    if (d?.autoComplete == true) runCatching { tasks.complete(d.taskId) }
                    repo.update(id, DelegationStatus.DONE, result = e.text)
                    notices.finished(id, title, ok = true)
                }
                is AgentEvent.Failed -> fail(id, title, e.reason)
            }
        }
    }

    private suspend fun fail(id: String, title: String, reason: String) {
        repo.addMessage(id, DelegationRole.AGENT, reason)
        repo.update(id, DelegationStatus.FAILED)
        notices.finished(id, title, ok = false)
    }

    suspend fun answer(delegationId: String, text: String) {
        val d = repo.get(delegationId) ?: return
        val ref = d.remoteRef ?: return
        repo.addMessage(delegationId, DelegationRole.USER, text)
        repo.update(delegationId, DelegationStatus.RUNNING)
        notices.clear(delegationId)
        agentFor(d.mode).answer(ref, text)
    }

    /** The user decided on an approval request; the (possibly edited) draft and recipients are what the agent uses. */
    suspend fun decide(approvalId: String, decision: String, draft: String, recipients: List<String>) {
        val a = repo.approval(approvalId) ?: return
        val d = repo.get(a.delegationId) ?: return
        repo.decide(approvalId, decision, draft, recipients)
        repo.update(d.id, DelegationStatus.RUNNING)
        notices.clear(d.id)
        val ref = d.remoteRef ?: return
        agentFor(d.mode).decide(ref, ApprovalDecision(decision, draft, recipients, DelegationRepository.requestId(a.editable)))
    }

    /** Partial mode: the user accepts the proposed subtasks; each becomes a subtask of the task. */
    suspend fun acceptSubtasks(delegationId: String, subtasks: List<String>) {
        val d = repo.get(delegationId) ?: return
        subtasks.filter { it.isNotBlank() }.forEach { tasks.add(TaskDraft(it.trim(), parentId = d.taskId)) }
    }

    /** The user confirms the work is done: the task is completed. */
    suspend fun confirmDone(delegationId: String) {
        val d = repo.get(delegationId) ?: return
        runCatching { tasks.complete(d.taskId) }
    }

    companion object {
        const val HERMES = "hermes"
        const val PARTIAL = "partial"
        const val AGENT_HERMES = "HERMES"
        const val AGENT_AI = "AI"
    }
}
