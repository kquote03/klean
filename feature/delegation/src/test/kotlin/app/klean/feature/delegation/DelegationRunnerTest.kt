package app.klean.feature.delegation

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.delegation.agent.AgentEvent
import app.klean.feature.delegation.agent.ApprovalDecision
import app.klean.feature.delegation.agent.DelegationAgent
import app.klean.feature.delegation.agent.DelegationPayload
import app.klean.feature.delegation.data.DelegationMode
import app.klean.feature.delegation.data.DelegationRepository
import app.klean.feature.delegation.data.DelegationRole
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.tasks.data.DefaultTasksRepository
import app.klean.feature.tasks.data.TaskDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A scripted agent: the test pushes events; answers and decisions are recorded. */
class ScriptedAgent : DelegationAgent {
    val events = Channel<AgentEvent>(Channel.UNLIMITED)
    val started = mutableListOf<DelegationPayload>()
    val answers = mutableListOf<String>()
    val decisions = mutableListOf<ApprovalDecision>()
    override suspend fun start(task: DelegationPayload): String { started += task; return "ref-${task.delegationId}" }
    override fun events(remoteRef: String): Flow<AgentEvent> = events.consumeAsFlow()
    override suspend fun answer(remoteRef: String, text: String) { answers += text }
    override suspend fun decide(remoteRef: String, approval: ApprovalDecision) { decisions += approval }
}

class RecordingNotices : DelegationNotices {
    /** Written on the runner's thread, read on the test's. */
    val log: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    override fun question(delegationId: String, taskTitle: String, text: String) { log += "question:$text" }
    override fun approval(approvalId: String, taskTitle: String, action: String, recipients: Int) { log += "approval:$recipients" }
    override fun finished(delegationId: String, taskTitle: String, ok: Boolean) { log += "finished:$ok" }
}

@RunWith(AndroidJUnit4::class)
class DelegationRunnerTest {
    private val clock = FakeClock(1_794_300_000_000L)
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val tasks = DefaultTasksRepository(db, stamper, clock, DubaiZone, SettingsRepository(db, stamper), emptySet())
    private val repo = DelegationRepository(db, stamper, clock, emptySet())
    private val hermes = ScriptedAgent()
    private val partial = ScriptedAgent()
    private val notices = RecordingNotices()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner = DelegationRunner(repo, tasks, hermes, partial, notices, scope)
    private val six = (1..6).map { "p$it@example.com" }

    @After fun close() {
        scope.cancel()
        db.close()
    }

    private suspend fun waitFor(what: String, check: suspend () -> Boolean) = withTimeout(10_000) {
        while (!check()) kotlinx.coroutines.delay(20)
    }

    @Test fun interactiveFlowQuestionApprovalEditedResult() = runBlocking {
        val taskId = tasks.add(TaskDraft("Invite the team to the offsite", notes = "Usual list"))
        val id = runner.delegate(taskId, DelegationMode.INTERACTIVE)
        waitFor("started") { repo.get(id)?.status == DelegationStatus.RUNNING }
        assertEquals("Invite the team to the offsite", hermes.started.single().title)
        assertEquals("ref-$id", repo.get(id)!!.remoteRef)

        hermes.events.send(AgentEvent.Question("Which date?"))
        waitFor("question") { repo.get(id)?.status == DelegationStatus.NEEDS_INPUT }
        runner.answer(id, "The 20th")
        assertEquals(listOf("The 20th"), hermes.answers)
        val msgs = repo.observeMessages(id).first()
        assertEquals(listOf(DelegationRole.AGENT to "Which date?", DelegationRole.USER to "The 20th"), msgs.map { it.role to it.text })

        hermes.events.send(AgentEvent.ApprovalNeeded("send_email", "Hi all", six))
        waitFor("approval") { repo.get(id)?.status == DelegationStatus.NEEDS_APPROVAL }
        val pending = repo.observePendingApprovals().first().single()
        assertEquals(six, DelegationRepository.decodeRecipients(pending.recipients))
        waitFor("approval notice") { "approval:6" in notices.log }

        runner.decide(pending.id, ApprovalDecision.EDITED, "Hi all, lunch included", six.take(4))
        val decided = repo.approval(pending.id)!!
        assertEquals("EDITED", decided.decision)
        assertEquals(4, DelegationRepository.decodeRecipients(decided.recipients).size)
        assertTrue(decided.decidedAt != null)
        assertEquals(ApprovalDecision(ApprovalDecision.EDITED, "Hi all, lunch included", six.take(4)), hermes.decisions.single())

        hermes.events.send(AgentEvent.Result("Sent to 4 people."))
        waitFor("done") { repo.get(id)?.status == DelegationStatus.DONE }
        assertEquals("Sent to 4 people.", repo.get(id)!!.result)
        // Not marked done until the user confirms (auto-complete off).
        assertEquals("OPEN", tasks.get(taskId)!!.status)
        runner.confirmDone(id)
        assertEquals("DONE", tasks.get(taskId)!!.status)
    }

    @Test fun agenticActsAloneUpToFiveRecipientsButSixAlwaysWait() = runBlocking {
        val taskId = tasks.add(TaskDraft("Send the minutes"))
        val id = runner.delegate(taskId, DelegationMode.AGENTIC, autoComplete = true)
        waitFor("started") { repo.get(id)?.status == DelegationStatus.RUNNING }
        hermes.events.send(AgentEvent.ApprovalNeeded("send_email", "Minutes", six.take(5)))
        waitFor("auto-approved") { hermes.decisions.size == 1 }
        assertEquals(ApprovalDecision.APPROVED, hermes.decisions.single().decision)
        assertTrue(repo.observePendingApprovals().first().isEmpty())

        hermes.events.send(AgentEvent.ApprovalNeeded("send_email", "Minutes", six))
        waitFor("waits") { repo.get(id)?.status == DelegationStatus.NEEDS_APPROVAL }
        assertEquals(1, repo.observePendingApprovals().first().size)
        assertEquals(1, hermes.decisions.size)

        val a = repo.observePendingApprovals().first().single()
        runner.decide(a.id, ApprovalDecision.REJECTED, a.draft, six)
        hermes.events.send(AgentEvent.Result("Didn't send."))
        waitFor("done") { repo.get(id)?.status == DelegationStatus.DONE }
        // Auto-complete on: the task is done.
        assertEquals("DONE", tasks.get(taskId)!!.status)
    }

    @Test fun partialModeUsesTheAiAgentAndOffersSubtasks() = runBlocking {
        val taskId = tasks.add(TaskDraft("Plan the move"))
        val id = runner.delegate(taskId, DelegationMode.PARTIAL)
        waitFor("started") { partial.started.isNotEmpty() }
        assertTrue(hermes.started.isEmpty())
        partial.events.send(AgentEvent.Result("A checklist", listOf("Book movers", "Pack books")))
        waitFor("done") { repo.get(id)?.status == DelegationStatus.DONE }
        runner.acceptSubtasks(id, listOf("Book movers", "Pack books"))
        assertEquals(listOf("Book movers", "Pack books"), tasks.subtasks(taskId).map { it.title })
        assertEquals("AI", repo.get(id)!!.agent)
    }

    @Test fun aFailingAgentLeavesTheTaskAndSaysWhy() = runBlocking {
        val taskId = tasks.add(TaskDraft("Call the bank"))
        val id = runner.delegate(taskId, DelegationMode.INTERACTIVE)
        waitFor("started") { repo.get(id)?.status == DelegationStatus.RUNNING }
        hermes.events.send(AgentEvent.Failed("events 500"))
        waitFor("failed") { repo.get(id)?.status == DelegationStatus.FAILED }
        // The status is written before the notice is posted; wait for both.
        waitFor("notice") { "finished:false" in notices.log }
        assertEquals("OPEN", tasks.get(taskId)!!.status)
    }

    /** Credentials never pass through Klean: the payload has no field that could carry one. */
    @Test fun payloadHasNoCredentialFields() {
        val names = DelegationPayload::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name.lowercase() }
        assertEquals(setOf("delegationid", "title", "notes", "due", "mode"), names.toSet())
        listOf("token", "password", "secret", "key", "credential", "auth", "cookie").forEach { w ->
            assertFalse(w, names.any { w in it })
        }
    }
}
