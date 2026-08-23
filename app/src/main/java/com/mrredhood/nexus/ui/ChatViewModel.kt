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
    private var workspaceId: String? = null
    private var generationJob: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _tokenUsage = MutableStateFlow(TokenUsage())
    val tokenUsage: StateFlow<TokenUsage> = _tokenUsage.asStateFlow()

    fun open(workspaceId: String) {
        if (this.workspaceId == workspaceId) return
        stop()
        this.workspaceId = workspaceId
        _messages.value = repository.load(workspaceId)
        _error.value = null
        _tokenUsage.value = TokenUsage()
    }

    fun send(text: String, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        val prompt = text.trim()
        if (prompt.isEmpty() || _generating.value) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _error.value = null
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
                } else {
                    provider.complete(request)
                }

                if (!result.success) {
                    _messages.value = history
                    _error.value = result.message
                    repository.save(id, history)
                } else {
                    val response = result.response
                    val finalText = response?.text ?: result.message
                    val current = _messages.value.toMutableList()
                    if (assistantIndex < current.size && current[assistantIndex].role == "assistant") {
                        current[assistantIndex] = current[assistantIndex].copy(content = finalText)
                    } else {
                        current.add(ChatMessage("assistant", finalText))
                    }
                    _messages.value = current
                    repository.save(id, current)
                    _tokenUsage.value = TokenUsage(
                        input = response?.inputTokens ?: estimateTokens(system + history.joinToString { it.content }),
                        output = response?.outputTokens ?: estimateTokens(finalText)
                    )
                }
            } catch (cancelled: CancellationException) {
                val current = _messages.value
                if (current.lastOrNull()?.role == "assistant" && current.last().content.isNotBlank()) {
                    repository.save(id, current)
                } else {
                    repository.save(id, history)
                }
                throw cancelled
            } catch (error: Throwable) {
                _messages.value = history
                _error.value = error.message ?: "AI request failed."
                repository.save(id, history)
            } finally {
                _generating.value = false
            }
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
        _error.value = null
        _tokenUsage.value = TokenUsage()
    }

    fun clearError() { _error.value = null }

    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    override fun onCleared() {
        generationJob?.cancel()
        super.onCleared()
    }
}

data class TokenUsage(val input: Int = 0, val output: Int = 0) {
    val total: Int get() = input + output
}
