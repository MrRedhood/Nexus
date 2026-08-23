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
            try {
                val system = contextBuilder.build(context)
                val requestMessages = buildList {
                    add(AiMessage("system", system))
                    history.takeLast(40).forEach { add(AiMessage(it.role, it.content)) }
                }
                val result = provider.complete(AiRequest(messages = requestMessages, model = "", stream = false))
                if (!result.success) {
                    _error.value = result.message
                } else {
                    val updated = _messages.value + ChatMessage("assistant", result.response?.text ?: result.message)
                    _messages.value = updated
                    repository.save(id, updated)
                }
            } finally {
                _generating.value = false
            }
        }
    }

    fun stop() { generationJob?.cancel(); generationJob = null; _generating.value = false }

    fun clear() {
        workspaceId?.let { repository.clear(it) }
        _messages.value = emptyList()
        _error.value = null
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        generationJob?.cancel()
        super.onCleared()
    }
}
