package com.mrredhood.nexus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.mrredhood.nexus.core.ai.NexusActionStatus
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
    private val _actionProposals = MutableStateFlow<List<NexusActionProposal>>(emptyList())
    val actionProposals: StateFlow<List<NexusActionProposal>> = _actionProposals.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun open(workspace: Workspace) {
        if (this.workspaceId == workspace.id) {
            this.workspace = workspace
            return
        }
        stop()
        this.workspaceId = workspace.id
        this.workspace = workspace
        _messages.value = repository.load(workspace.id)
        _actionProposals.value = NexusActionProtocol.extract(_messages.value.lastOrNull { it.role == "assistant" }?.content.orEmpty())
        _error.value = null
        _actionMessage.value = null
        _tokenUsage.value = TokenUsage()
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
                    _actionProposals.value = NexusActionProtocol.extract(finalText)
                    _tokenUsage.value = TokenUsage(
                        input = response?.inputTokens ?: estimateTokens(system + history.joinToString { it.content }),
                        output = response?.outputTokens ?: estimateTokens(finalText)
                    )
                }
            } catch (cancelled: CancellationException) {
                val current = _messages.value
                if (current.lastOrNull()?.role == "assistant" && current.last().content.isNotBlank()) {
                    repository.save(id, current)
                    _actionProposals.value = NexusActionProtocol.extract(current.last().content)
                } else repository.save(id, history)
                throw cancelled
            } catch (error: Throwable) {
                _messages.value = history
                _error.value = error.message ?: "AI request failed."
                repository.save(id, history)
            } finally { _generating.value = false }
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
        _generating.value = false
    }

    fun clear() {
        stop()
        workspaceId?.let(repository::clear)
        _messages.value = emptyList()
        _actionProposals.value = emptyList()
        _actionMessage.value = null
        _error.value = null
        _tokenUsage.value = TokenUsage()
    }

    fun clearError() { _error.value = null }

    fun rejectAction(id: String) {
        _actionProposals.value = _actionProposals.value.map { proposal -> if (proposal.id == id) proposal.copy(status = NexusActionStatus.REJECTED) else proposal }
    }

    fun approveAction(id: String) {
        val target = _actionProposals.value.firstOrNull { it.id == id } ?: return
        if (target.status == NexusActionStatus.REJECTED || target.status == NexusActionStatus.COMPLETED) return
        viewModelScope.launch {
            _actionProposals.value = _actionProposals.value.map { proposal -> if (proposal.id == id) proposal.copy(status = NexusActionStatus.EXECUTING) else proposal }
            val result = if (workspace == null) {
                com.mrredhood.nexus.core.ai.ActionExecutionResult(false, "Open a workspace before applying this action.")
            } else {
                actionExecutor.execute(workspace!!, target.action)
            }
            _actionProposals.value = _actionProposals.value.map { proposal ->
                if (proposal.id == id) proposal.copy(status = if (result.success) NexusActionStatus.COMPLETED else NexusActionStatus.FAILED) else proposal
            }
            _actionMessage.value = result.message
        }
    }

    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    override fun onCleared() {
        generationJob?.cancel()
        super.onCleared()
    }
}

data class TokenUsage(val input: Int = 0, val output: Int = 0) {
    val total: Int get() = input + output
}
