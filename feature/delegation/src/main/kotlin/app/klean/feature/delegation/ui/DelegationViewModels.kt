package app.klean.feature.delegation.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.klean.core.data.db.entity.ApprovalRequestEntity
import app.klean.core.data.db.entity.DelegationEntity
import app.klean.core.data.db.entity.DelegationMessageEntity
import app.klean.feature.delegation.DelegationRunner
import app.klean.feature.delegation.agent.ApprovalDecision
import app.klean.feature.delegation.data.DelegationRepository
import app.klean.feature.delegation.data.DelegationRole
import app.klean.feature.delegation.data.DelegationStatus
import app.klean.feature.delegation.hermes.HermesSettings
import app.klean.feature.tasks.data.TasksRepository
import app.klean.shared.delegation.ApprovalPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The approval screen: the full draft and the full recipient list, both editable (plan M23). */
data class ApprovalUi(
    val loaded: Boolean = false,
    val taskTitle: String = "",
    val action: String = "",
    val draft: String = "",
    val recipients: List<String> = emptyList(),
    val originalDraft: String = "",
    val originalRecipients: List<String> = emptyList(),
    val decision: String = "PENDING",
    val done: Boolean = false,
) {
    val edited: Boolean get() = draft != originalDraft || recipients != originalRecipients
    /** More than [ApprovalPolicy.MAX_RECIPIENTS_WITHOUT_APPROVAL] recipients: said plainly above the list. */
    val many: Boolean get() = recipients.size > ApprovalPolicy.MAX_RECIPIENTS_WITHOUT_APPROVAL
}

@HiltViewModel
class ApprovalViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: DelegationRepository,
    private val tasks: TasksRepository,
    private val runner: DelegationRunner,
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("approvalId"))
    private val _state = MutableStateFlow(ApprovalUi())
    val state: StateFlow<ApprovalUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val a = repo.approval(id) ?: run { _state.value = ApprovalUi(loaded = true, done = true); return@launch }
            val title = repo.get(a.delegationId)?.let { tasks.get(it.taskId)?.title }.orEmpty()
            val r = DelegationRepository.decodeRecipients(a.recipients)
            _state.value = ApprovalUi(true, title, a.action, a.draft, r, a.draft, r, a.decision, done = a.decision != "PENDING")
        }
    }

    fun setDraft(v: String) { _state.value = _state.value.copy(draft = v) }
    fun setRecipient(i: Int, v: String) {
        val l = _state.value.recipients.toMutableList()
        if (i in l.indices) l[i] = v
        _state.value = _state.value.copy(recipients = l)
    }
    fun removeRecipient(i: Int) { _state.value = _state.value.copy(recipients = _state.value.recipients.filterIndexed { j, _ -> j != i }) }
    fun addRecipient(v: String) {
        val t = v.trim()
        if (t.isNotEmpty()) _state.value = _state.value.copy(recipients = _state.value.recipients + t)
    }

    fun approve() = decide(if (_state.value.edited) ApprovalDecision.EDITED else ApprovalDecision.APPROVED)
    fun reject() = decide(ApprovalDecision.REJECTED)

    private fun decide(decision: String) = viewModelScope.launch {
        val s = _state.value
        runner.decide(id, decision, s.draft, s.recipients.map { it.trim() }.filter { it.isNotEmpty() })
        _state.value = s.copy(decision = decision, done = true)
    }
}

/** One delegation: status, the conversation, approvals, the result. */
data class DelegationUi(
    val loaded: Boolean = false,
    val delegation: DelegationEntity? = null,
    val taskTitle: String = "",
    val messages: List<DelegationMessageEntity> = emptyList(),
    val approvals: List<ApprovalRequestEntity> = emptyList(),
) {
    val needsInput: Boolean get() = delegation?.status == DelegationStatus.NEEDS_INPUT
    /** Partial mode's proposed subtasks: the agent's last \"• …\" message. */
    val proposedSubtasks: List<String>
        get() = messages.lastOrNull { it.role == DelegationRole.AGENT && it.text.startsWith("• ") }
            ?.text?.lines()?.map { it.removePrefix("• ").trim() }?.filter { it.isNotEmpty() }.orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DelegationViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: DelegationRepository,
    private val tasks: TasksRepository,
    private val runner: DelegationRunner,
) : ViewModel() {
    private val id: String = checkNotNull(handle.get<String>("delegationId"))
    private val title = MutableStateFlow("")
    private val accepted = MutableStateFlow(false)
    val subtasksAccepted: StateFlow<Boolean> = accepted.asStateFlow()

    init {
        viewModelScope.launch { title.value = repo.get(id)?.let { tasks.get(it.taskId)?.title }.orEmpty() }
    }

    val state: StateFlow<DelegationUi> = combine(repo.observe(id), repo.observeMessages(id), repo.observeApprovals(id), title) { d, m, a, t ->
        DelegationUi(true, d, t, m, a)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DelegationUi())

    fun answer(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { runner.answer(id, text.trim()) }
    }

    fun acceptSubtasks(list: List<String>) = viewModelScope.launch {
        runner.acceptSubtasks(id, list)
        accepted.value = true
    }

    fun confirmDone(onDone: () -> Unit) = viewModelScope.launch {
        runner.confirmDone(id)
        onDone()
    }
}

/** Starting a delegation from the task screen, and the task's latest one. */
@HiltViewModel
class DelegateViewModel @Inject constructor(
    private val repo: DelegationRepository,
    private val runner: DelegationRunner,
    private val hermes: HermesSettings,
) : ViewModel() {
    private val taskId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val latest: StateFlow<DelegationEntity?> = taskId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeForTask(id) }
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hermesReady = MutableStateFlow(false)
    val hermesReady: StateFlow<Boolean> = _hermesReady.asStateFlow()

    fun bind(id: String) {
        taskId.value = id
        viewModelScope.launch { _hermesReady.value = hermes.configured() }
    }

    fun delegate(mode: String, autoComplete: Boolean, onStarted: (String) -> Unit) = viewModelScope.launch {
        val id = taskId.value ?: return@launch
        onStarted(runner.delegate(id, mode, autoComplete))
    }
}

/** Today's APPROVALS card and the Delegation list: pending approvals with their task titles. */
data class PendingApproval(val approval: ApprovalRequestEntity, val taskTitle: String)

@HiltViewModel
class ApprovalsCardViewModel @Inject constructor(repo: DelegationRepository, tasks: TasksRepository) : ViewModel() {
    val pending: StateFlow<List<PendingApproval>?> = repo.observePendingApprovals().map { list ->
        list.map { a -> PendingApproval(a, repo.get(a.delegationId)?.let { tasks.get(it.taskId)?.title }.orEmpty()) }
    }.map<List<PendingApproval>, List<PendingApproval>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Settings › Delegation: the Hermes URL and token. */
data class HermesSettingsUi(val loaded: Boolean = false, val url: String = "", val token: String = "", val saved: Boolean = false, val test: HermesSettings.TestResult? = null)

@HiltViewModel
class HermesSettingsViewModel @Inject constructor(private val hermes: HermesSettings) : ViewModel() {
    private val _state = MutableStateFlow(HermesSettingsUi())
    val state: StateFlow<HermesSettingsUi> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = HermesSettingsUi(true, hermes.url().orEmpty(), hermes.token().orEmpty()) }
    }

    fun setUrl(v: String) { _state.value = _state.value.copy(url = v, saved = false, test = null) }
    fun setToken(v: String) { _state.value = _state.value.copy(token = v, saved = false, test = null) }

    fun save() = viewModelScope.launch {
        hermes.save(_state.value.url, _state.value.token)
        _state.value = _state.value.copy(saved = true)
    }

    fun test() = viewModelScope.launch {
        hermes.save(_state.value.url, _state.value.token)
        _state.value = _state.value.copy(saved = true, test = hermes.test())
    }
}
