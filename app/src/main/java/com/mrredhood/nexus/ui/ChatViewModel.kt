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
import com.mrredhood.nexus.core.settings.NexusSettingsRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)
    private val provider = AiProviderService(application.applicationContext)
    private val contextBuilder = ChatContextBuilder()
    private var workspaceId: String? = null
    private var generationJob: Job? = null
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _tokenUsage = MutableStateFlow<TokenUsage>(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()

    fun open(workspaceId: String) {
        if (this.workspaceId == workspaceId) return
        generationJob?.cancel()
        this.workspaceId = workspaceId
        _messages.value = repository.load(workspaceId)
        _error.value = null
    }

    fun send(text: String, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _generating.value) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _error.value = null
            val history = _messages.value + ChatMessage("user", prompt)
            _messages.value = history
            repository.save(id, history)
            _generating.value = true
            val assistant = ChatMessage("assistant", "")
            _messages.value = history + assistant
            try {
                val system = contextBuilder.build(context)
                val request = AiRequest(
                    messages = buildList {
                        add(AiMessage("system", system))
                        history.takeLast(40).forEach { add(AiMessage(it.role, it.content)) }
                    },
                    model = "",
                    stream = NexusSettingsRuntime.current().aiStreaming
                )
                val streamed = request.stream && NexusSettingsRuntime.current().aiStreaming
                val result = if (streamed) {
                    provider.stream(request) { delta ->
                        val current = _messages.value.toMutableList()
                        val last = current.lastIndex
                        if (last >= 0 && current[last].role == "assistant") {
                            current[last] = current[last].copy(content = current[last].content + delta)
                            _messages.value = current
                        }
                    }
                } else provider.complete(request)
                if (!result.success) {
                    _messages.value = history
                    _error.value = result.message
                } else {
                    val response = result.response
                    val finalText = response?.text ?: result.message
                    val current = _messages.value.toMutableList()
                    val last = current.lastIndex
                    if (last >= 0 && current[last].role == "assistant") current[last] = current[last].copy(content = finalText)
                    else current.add(ChatMessage("assistant", finalText))
                    _messages.value = current
                    repository.save(id, current)
                    _tokenUsage.value = TokenUsage(response?.inputTokens ?: estimateTokens(system), response?.outputTokens ?: estimateTokens(finalText))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                val current = _messages.value
                val partial = current.lastOrNull()?.content.orEmpty()
                if (partial.isNotBlank()) repository.save(id, current)
                throw cancelled
            } finally { _generating.value = false }
        }
    }

    fun stop() { generationJob?.cancel(); generationJob = null; _generating.value = false }
    fun clear() { workspaceId?.let(repository::clear); _messages.value = emptyList(); _error.value = null; _tokenUsage.value = TokenUsage() }
    fun clearError() { _error.value = null }
    private fun estimateTokens(text: String) = (text.length + 3) / 4
    override fun onCleared() { generationJob?.cancel(); super.onCleared() }
}

data class TokenUsage(val input: Int = 0, val output: Int = 0) {
    val total: Int get() = input + output
}
