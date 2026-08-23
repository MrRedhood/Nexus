package com.mrredhood.nexus.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live bridge between the Android workspace/editor runtime and Nexus AI chat.
 *
 * UI layers update this store as the active editor, workspace summary, Git
 * integration, or terminal output changes. ChatScreen observes it so the
 * ChatViewModel always receives the latest context when a message is sent.
 */
object ChatContextStore {
    private val _contexts = MutableStateFlow<Map<String, ChatContext>>(emptyMap())
    val contexts: StateFlow<Map<String, ChatContext>> = _contexts.asStateFlow()

    fun context(workspaceId: String): ChatContext = _contexts.value[workspaceId] ?: ChatContext()

    fun update(workspaceId: String, transform: (ChatContext) -> ChatContext) {
        val current = _contexts.value[workspaceId] ?: ChatContext()
        _contexts.value = _contexts.value + (workspaceId to transform(current))
    }

    fun updateCurrentFile(workspaceId: String, relativePath: String?, content: String?) {
        update(workspaceId) { current ->
            current.copy(
                currentFile = if (relativePath.isNullOrBlank() || content.isNullOrBlank()) null
                else "PATH: $relativePath\n\n$content"
            )
        }
    }

    fun updateSelection(workspaceId: String, selection: String?) {
        update(workspaceId) { it.copy(selection = selection?.takeIf(String::isNotBlank)) }
    }

    fun updateGitDiff(workspaceId: String, diff: String?) {
        update(workspaceId) { it.copy(gitDiff = diff?.takeIf(String::isNotBlank)) }
    }

    fun updateTerminalOutput(workspaceId: String, output: String?) {
        update(workspaceId) { it.copy(terminalOutput = output?.takeIf(String::isNotBlank)) }
    }

    fun updateWorkspaceSummary(workspaceId: String, summary: String?) {
        update(workspaceId) { it.copy(workspaceSummary = summary?.takeIf(String::isNotBlank)) }
    }

    fun clear(workspaceId: String) {
        _contexts.value = _contexts.value - workspaceId
    }
}
