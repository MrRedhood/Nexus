package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.nexus.core.ai.AIContextItem
import com.mrredhood.nexus.core.ai.AIContextOptions
import com.mrredhood.nexus.core.ai.AIContextService
import com.mrredhood.nexus.core.ai.AIContextSnapshot
import com.mrredhood.nexus.core.ai.AIContextSource
import com.mrredhood.nexus.core.ai.AiMessage
import com.mrredhood.nexus.core.ai.AiProviderService
import com.mrredhood.nexus.core.ai.AiRequest
import com.mrredhood.nexus.core.ai.ChatContext
import com.mrredhood.nexus.core.ai.ChatContextBuilder
import com.mrredhood.nexus.core.ai.ChatMessage
import com.mrredhood.nexus.core.ai.ChatRepository
import com.mrredhood.nexus.core.ai.NexusActionExecutor
import com.mrredhood.nexus.core.ai.NexusActionPolicy
import com.mrredhood.nexus.core.ai.NexusActionProposal
import com.mrredhood.nexus.core.ai.NexusActionProtocol
import com.mrredhood.nexus.core.ai.NexusActionReview
import com.mrredhood.nexus.core.ai.NexusActionStatus
import com.mrredhood.nexus.core.ai.NexusEditorActionBus
import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)
    private val provider = AiProviderService(application.applicationContext)
    private val contextBuilder = ChatContextBuilder()
    private val contextService = AIContextService()
    private val actionExecutor = NexusActionExecutor(WorkspaceFileSystem(application.applicationContext))
    private var workspaceId: String? = null
    private var workspace: Workspace? = null
    private var generationJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()
    private val _contextSnapshot = MutableStateFlow<AIContextSnapshot?>(null)
    val contextSnapshot: StateFlow<AIContextSnapshot?> = _contextSnapshot.asStateFlow()
    private val _actionProposals = MutableStateFlow<List<NexusActionProposal>>(emptyList())
    val actionProposals: StateFlow<List<NexusActionProposal>> = _actionProposals.asStateFlow()
    private val _actionReviews = MutableStateFlow<Map<String, NexusActionReview>>(emptyMap())
    val actionReviews: StateFlow<Map<String, NexusActionReview>> = _actionReviews.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun open(workspace: Workspace) {
        if (this.workspaceId == workspace.id) { this.workspace = workspace; return }
        stop()
        this.workspaceId = workspace.id
        this.workspace = workspace
        _messages.value = repository.load(workspace.id)
        val proposals = NexusActionProtocol.extract(_messages.value.lastOrNull { it.role == "assistant" }?.content.orEmpty())
        _actionProposals.value = proposals
        _actionReviews.value = emptyMap()
        previewMutatingActions(proposals, workspace)
        _error.value = null
        _actionMessage.value = null
        _tokenUsage.value = TokenUsage()
        _contextSnapshot.value = null
    }

    fun inspectContext(userMessage: String, context: ChatContext) {
        val settings = NexusSettingsRuntime.current()
        val request = com.mrredhood.nexus.core.ai.AIContextRequest(
            userMessage = userMessage,
            currentFile = context.currentFile?.let { contextItem(AIContextSource.CURRENT_FILE, "Current file", it) },
            selection = context.selection?.let { contextItem(AIContextSource.SELECTION, "Selected code", it) },
            gitDiff = context.gitDiff?.let { contextItem(AIContextSource.GIT_DIFF, "Current changes", it) },
            terminalOutput = context.terminalOutput?.let { contextItem(AIContextSource.TERMINAL_OUTPUT, "Recent terminal output", it) },
            workspaceSummary = context.workspaceSummary?.let { contextItem(AIContextSource.WORKSPACE_SUMMARY, "Workspace summary", it) }
        )
        val automaticMode = when (settings.workspaceContext.lowercase()) {
            "never", "off", "disabled" -> com.mrredhood.nexus.core.ai.AutomaticContextMode.NEVER
            "always" -> com.mrredhood.nexus.core.ai.AutomaticContextMode.ALWAYS
            else -> com.mrredhood.nexus.core.ai.AutomaticContextMode.SMART
        }
        _contextSnapshot.value = contextService.assemble(
            request,
            AIContextOptions(
                automaticContext = automaticMode,
                maxRelatedFiles = settings.maxContextFiles.coerceIn(0, 50),
                includeCurrentFile = settings.includeCurrentFile,
                includeSelection = settings.includeSelection,
                includeGitDiff = settings.includeGitDiff,
                includeTerminalOutput = settings.includeTerminalContext,
                includeWorkspaceSummary = settings.includeWorkspaceSummary
            )
        )
    }

    private fun contextItem(source: AIContextSource, label: String, content: String) = AIContextItem(
        source = source,
        label = label,
        content = content,
        estimatedTokens = AIContextService.estimateTokens(content)
    )

    private fun previewMutatingActions(proposals: List<NexusActionProposal>, targetWorkspace: Workspace) {
        viewModelScope.launch {
            val reviews = mutableMapOf<String, NexusActionReview>()
            proposals.filter { NexusActionPolicy.requiresApproval(it.action) }.forEach { proposal ->
                runCatching { actionExecutor.preview(targetWorkspace, proposal) }.onSuccess { review ->
                    if (review != null) reviews[proposal.id] = review
                }
            }
            _actionReviews.value = reviews
        }
    }

    fun send(text: String, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _generating.value) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _error.value = null
            _actionMessage.value = null
            _actionProposals.value = emptyList()
            _actionReviews.value = emptyMap()
            inspectContext(prompt, context)
            val settings = NexusSettingsRuntime.current()
            val history = _messages.value + ChatMessage("user", prompt)
            val system = contextBuilder.build(context)
            val assistantIndex = history.size
            _messages.value = history + ChatMessage("assistant", "")
            repository.save(id, history)
            _generating.value = true
            try {
                val request = AiRequest(
                    messages = buildList {
                        add(AiMessage("system", system))
                        history.takeLast(40).forEach { add(AiMessage(it.role, it.content)) }
                    },
                    model = "",
                    stream = settings.aiStreaming
                )
                val result = if (settings.aiStreaming) {
                    provider.stream(request) { delta ->
                        val current = _messages.value.toMutableList()
                        if (assistantIndex < current.size && current[assistantIndex].role == "assistant") {
                            current[assistantIndex] = current[assistantIndex].copy(content = current[assistantIndex].content + delta)
                            _messages.value = current
                        }
                    }
                } else provider.complete(request)
                if (!result.success) {
                    _messages.value = history
                    _error.value = result.message
                    repository.save(id, history)
                } else {
                    val response = result.response
                    val finalText = response?.text ?: result.message
                    val current = _messages.value.toMutableList()
                    if (assistantIndex < current.size && current[assistantIndex].role == "assistant") current[assistantIndex] = current[assistantIndex].copy(content = finalText)
                    else current.add(ChatMessage("assistant", finalText))
                    _messages.value = current
                    repository.save(id, current)
                    val proposals = NexusActionProtocol.extract(finalText)
                    _actionProposals.value = proposals
                    workspace?.let { previewMutatingActions(proposals, it) }
                    _tokenUsage.value = TokenUsage(
                        input = response?.inputTokens ?: estimateTokens(system + history.joinToString { it.content }),
                        output = response?.outputTokens ?: estimateTokens(finalText)
                    )
                }
            } catch (cancelled: CancellationException) {
                val current = _messages.value
                if (current.lastOrNull()?.role == "assistant" && current.last().content.isNotBlank()) {
                    repository.save(id, current)
                    val proposals = NexusActionProtocol.extract(current.last().content)
                    _actionProposals.value = proposals
                    workspace?.let { previewMutatingActions(proposals, it) }
                } else repository.save(id, history)
                throw cancelled
            } catch (error: Throwable) {
                _messages.value = history
                _error.value = error.message ?: "AI request failed."
                repository.save(id, history)
            } finally { _generating.value = false }
        }
    }

    fun stop() { generationJob?.cancel(); generationJob = null; _generating.value = false }

    fun clear() {
        stop()
        workspaceId?.let(repository::clear)
        _messages.value = emptyList()
        _actionProposals.value = emptyList()
        _actionReviews.value = emptyMap()
        _actionMessage.value = null
        _error.value = null
        _tokenUsage.value = TokenUsage()
        _contextSnapshot.value = null
    }

    fun clearError() { _error.value = null }

    fun rejectAction(id: String) {
        _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.REJECTED) else it }
    }

    fun openAction(id: String) {
        val target = _actionProposals.value.firstOrNull { it.id == id } ?: return
        val path = target.action.path ?: return
        if (target.action.type == "open_file" || target.action.type == "focus_file") {
            workspace?.let { NexusEditorActionBus.request(it.id, path, target.action.type == "focus_file") }
        }
    }

    fun approveAction(id: String) {
        val target = _actionProposals.value.firstOrNull { it.id == id } ?: return
        if (target.status == NexusActionStatus.REJECTED || target.status == NexusActionStatus.COMPLETED || target.status == NexusActionStatus.EXECUTING) return
        val targetWorkspace = workspace ?: run { _actionMessage.value = "Open a workspace before applying this action."; return }
        viewModelScope.launch {
            if (NexusActionPolicy.requiresApproval(target.action)) {
                val freshReview = runCatching { actionExecutor.preview(targetWorkspace, target) }.getOrElse {
                    _actionMessage.value = it.message ?: "Unable to prepare action review."
                    return@launch
                }
                if (freshReview == null) {
                    _actionMessage.value = "This action cannot be reviewed safely."
                    return@launch
                }
                _actionReviews.value = _actionReviews.value + (id to freshReview)
                if (!freshReview.changed) {
                    _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.COMPLETED) else it }
                    _actionMessage.value = "No changes to apply for ${freshReview.path}."
                    return@launch
                }
            }
            _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.EXECUTING) else it }
            val result = actionExecutor.execute(targetWorkspace, target.action)
            _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = if (result.success) NexusActionStatus.COMPLETED else NexusActionStatus.FAILED) else it }
            _actionMessage.value = result.message
            if (result.success && (target.action.type == "open_file" || target.action.type == "focus_file")) openAction(id)
            if (result.success && NexusActionPolicy.requiresApproval(target.action)) {
                actionExecutor.preview(targetWorkspace, target)?.let { _actionReviews.value = _actionReviews.value + (id to it) }
            }
        }
    }

    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    override fun onCleared() { generationJob?.cancel(); super.onCleared() }
}

data class TokenUsage(val input: Int = 0, val output: Int = 0) { val total: Int get() = input + output }
