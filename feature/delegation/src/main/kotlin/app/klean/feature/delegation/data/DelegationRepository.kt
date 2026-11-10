package app.klean.feature.delegation.data

import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.ApprovalRequestEntity
import app.klean.core.data.db.entity.DelegationEntity
import app.klean.core.data.db.entity.DelegationMessageEntity
import app.klean.core.data.db.transact
import app.klean.core.data.sync.SyncStamper
import app.klean.shared.id.UuidV7
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object DelegationStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val NEEDS_INPUT = "NEEDS_INPUT"
    const val NEEDS_APPROVAL = "NEEDS_APPROVAL"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

object DelegationMode {
    const val PARTIAL = "PARTIAL"
    const val INTERACTIVE = "INTERACTIVE"
    const val AGENTIC = "AGENTIC"
}

object DelegationRole {
    const val AGENT = "AGENT"
    const val USER = "USER"
}

/** Told after every delegation write (Today card, status line). */
fun interface DelegationChangeListener {
    suspend fun onDelegationsChanged()
}

/** Delegations, their messages and approval requests. Every write is stamped for sync; nothing is hard-deleted. */
@Singleton
class DelegationRepository @Inject constructor(
    private val db: KleanDatabase,
    private val stamper: SyncStamper,
    private val clock: Clock,
    private val listeners: Set<@JvmSuppressWildcards DelegationChangeListener>,
) {
    private val delegations get() = db.delegationDao()
    private val messages get() = db.delegationMessageDao()
    private val approvals get() = db.approvalRequestDao()
    private fun now() = clock.now().toEpochMilliseconds()

    fun observe(id: String): Flow<DelegationEntity?> = delegations.observeAll().map { l -> l.firstOrNull { it.id == id } }
    fun observeForTask(taskId: String): Flow<List<DelegationEntity>> = delegations.observeForTask(taskId)
    fun observeMessages(delegationId: String): Flow<List<DelegationMessageEntity>> = messages.observeForDelegation(delegationId)
    fun observeApprovals(delegationId: String): Flow<List<ApprovalRequestEntity>> = approvals.observeForDelegation(delegationId)
    fun observePendingApprovals(): Flow<List<ApprovalRequestEntity>> = approvals.observePending()
    fun observeActive(): Flow<List<DelegationEntity>> = delegations.observeActive()

    suspend fun get(id: String): DelegationEntity? = delegations.get(id)?.takeUnless { it.sync.deleted }
    suspend fun approval(id: String): ApprovalRequestEntity? = approvals.get(id)?.takeUnless { it.sync.deleted }

    suspend fun create(taskId: String, mode: String, agent: String, autoComplete: Boolean = false): String {
        val id = db.transact {
            UuidV7.generate(now()).also {
                delegations.upsert(DelegationEntity(it, taskId, mode, agent, autoComplete = autoComplete, sync = stamper.stamp()))
            }
        }
        changed()
        return id
    }

    suspend fun update(id: String, status: String? = null, remoteRef: String? = null, result: String? = null) {
        db.transact {
            val d = delegations.get(id) ?: return@transact
            delegations.upsert(
                d.copy(
                    status = status ?: d.status, remoteRef = remoteRef ?: d.remoteRef, result = result ?: d.result,
                    sync = stamper.stamp(d.sync),
                ),
            )
        }
        changed()
    }

    suspend fun addMessage(delegationId: String, role: String, text: String): String {
        val id = db.transact {
            UuidV7.generate(now()).also { messages.upsert(DelegationMessageEntity(it, delegationId, role, text, now(), stamper.stamp())) }
        }
        changed()
        return id
    }

    suspend fun addApproval(delegationId: String, action: String, draft: String, recipients: List<String>, requestId: String? = null): String {
        val id = db.transact {
            UuidV7.generate(now()).also {
                approvals.upsert(
                    ApprovalRequestEntity(
                        id = it, delegationId = delegationId, action = action, draft = draft,
                        recipients = encodeRecipients(recipients),
                        // The native Hermes prompt id rides along so the decision can answer it.
                        editable = if (requestId != null) """{"draft":true,"recipients":true,"request_id":${Json.encodeToString(String.serializer(), requestId)}}""" else EDITABLE,
                        sync = stamper.stamp(),
                    ),
                )
            }
        }
        changed()
        return id
    }

    suspend fun decide(approvalId: String, decision: String, draft: String, recipients: List<String>) {
        db.transact {
            val a = approvals.get(approvalId) ?: return@transact
            approvals.upsert(
                a.copy(decision = decision, draft = draft, recipients = encodeRecipients(recipients), decidedAt = now(), sync = stamper.stamp(a.sync)),
            )
        }
        changed()
    }

    private suspend fun changed() = listeners.forEach { runCatching { it.onDelegationsChanged() } }

    companion object {
        const val EDITABLE = """{"draft":true,"recipients":true}"""
        private val list = ListSerializer(String.serializer())
        fun encodeRecipients(r: List<String>): String = Json.encodeToString(list, r.map { it.trim() }.filter { it.isNotEmpty() })
        fun decodeRecipients(json: String): List<String> = runCatching { Json.decodeFromString(list, json) }.getOrDefault(emptyList())
        fun requestId(editable: String): String? =
            runCatching { Json.parseToJsonElement(editable).let { (it as kotlinx.serialization.json.JsonObject)["request_id"] } }
                .getOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }
}
