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
import com.mrredhood.nexus.core.ai.AutomaticContextMode
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
import com.mrredhood.nexus.core.ai.NexusAgentLoop
import com.mrredhood.nexus.core.ai.NexusEditorActionBus
import com.mrredhood.nexus.core.ai.WorkspaceContextService
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
    private val workspaceContextService = WorkspaceContextService(WorkspaceFileSystem(application.applicationContext))
    private val actionExecutor = NexusActionExecutor(WorkspaceFileSystem(application.applicationContext))
    private val agentLoop = NexusAgentLoop(actionExecutor, maxRounds = 5)
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
        workspaceId = workspace.id
        this.workspace = workspace
        _messages.value = repository.load(workspace.id)
        _actionProposals.value = NexusActionProtocol.extract(_messages.value.lastOrNull { it.role == "assistant" }?.content.orEmpty())
        _actionReviews.value = emptyMap()
        _error.value = null
        _actionMessage.value = null
        _tokenUsage.value = TokenUsage()
        _contextSnapshot.value = null
        previewMutatingActions(_actionProposals.value, workspace)
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
            "never", "off", "disabled" -> AutomaticContextMode.NEVER
            "always" -> AutomaticContextMode.ALWAYS
            else -> AutomaticContextMode.SMART
        }
        _contextSnapshot.value = contextService.assemble(request, AIContextOptions(
            automaticContext = automaticMode,
            maxRelatedFiles = settings.maxContextFiles.coerceIn(0, 50),
            includeCurrentFile = settings.includeCurrentFile,
            includeSelection = settings.includeSelection,
            includeGitDiff = settings.includeGitDiff,
            includeTerminalOutput = settings.includeTerminalContext,
            includeWorkspaceSummary = settings.includeWorkspaceSummary
        ))
    }

    private fun contextItem(source: AIContextSource, label: String, content: String): AIContextItem = AIContextItem(source = source, label = label, content = content, estimatedTokens = AIContextService.estimateTokens(content))

    private fun previewMutatingActions(proposals: List<NexusActionProposal>, targetWorkspace: Workspace) {
        viewModelScope.launch {
            val reviews = mutableMapOf<String, NexusActionReview>()
            proposals.filter { NexusActionPolicy.requiresApproval(it.action) }.forEach { proposal ->
                runCatching { actionExecutor.preview(targetWorkspace, proposal) }.onSuccess { review -> if (review != null) reviews[proposal.id] = review }
            }
            _actionReviews.value = reviews
        }
    }

    /** Replaces a user message and all responses after it, then sends the edited prompt. */
    fun editAndResend(index: Int, editedText: String, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        if (_generating.value) return
        val text = editedText.trim()
        if (text.isEmpty()) return
        val target = _messages.value.getOrNull(index) ?: return
        if (target.role != "user") return
        _messages.value = _messages.value.take(index)
        repository.save(id, _messages.value)
        send(text, context)
    }

    /** Removes the selected assistant response and regenerates it from the preceding user message. */
    fun regenerate(index: Int, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        if (_generating.value) return
        val current = _messages.value
        val target = current.getOrNull(index) ?: return
        if (target.role != "assistant") return
        val userIndex = current.subList(0, index).indexOfLast { it.role == "user" }
        if (userIndex < 0) return
        val prompt = current[userIndex].content.trim()
        if (prompt.isEmpty()) return
        _messages.value = current.take(userIndex)
        repository.save(id, _messages.value)
        send(prompt, context)
    }

    fun send(text: String, context: ChatContext = ChatContext()) {
        val id = workspaceId ?: return
        val rawPrompt = text.trim()
        if (rawPrompt.isEmpty() || _generating.value) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _error.value = null
            _actionMessage.value = null
            _actionProposals.value = emptyList()
            _actionReviews.value = emptyMap()
            val prompt = normalizeCommand(rawPrompt)
            inspectContext(prompt, context)
            val settings = NexusSettingsRuntime.current()
            val previous = _messages.value
            val history = previous + ChatMessage("user", rawPrompt)
            val snapshot = _contextSnapshot.value
            val system = contextBuilder.build(context, snapshot) + "\n\n" + WORKSPACE_TOOL_INSTRUCTIONS
            val assistantIndex = history.size
            _messages.value = history + ChatMessage("assistant", "")
            _generating.value = true
            try {
                val providerMessages = history.takeLast(40).map { AiMessage(it.role, it.content) }.toMutableList()
                var finalText = ""
                var finalResponse: com.mrredhood.nexus.core.ai.AiResponse? = null
                var round = 0
                var shouldContinue = true
                while (shouldContinue && agentLoop.canContinue(round)) {
                    val request = AiRequest(messages = buildList { add(AiMessage("system", system)); addAll(providerMessages) }, model = "", stream = settings.aiStreaming && round == 0)
                    val result = if (settings.aiStreaming && round == 0) {
                        provider.stream(request) { delta -> finalText += delta; updateAssistant(assistantIndex, finalText) }
                    } else provider.complete(request)
                    if (!result.success) throw IllegalStateException(result.message.ifBlank { "AI request failed." })
                    finalResponse = result.response
                    finalText = result.response?.text ?: result.message
                    updateAssistant(assistantIndex, NexusActionProtocol.stripProtocol(finalText))
                    val proposals = NexusActionProtocol.extract(finalText)
                    _actionProposals.value = proposals
                    val activeWorkspace = workspace
                    if (activeWorkspace == null || proposals.isEmpty()) shouldContinue = false
                    else {
                        val toolResults = agentLoop.collectReadOnlyResults(activeWorkspace, proposals)
                        val mutating = proposals.filter { NexusActionPolicy.requiresApproval(it.action) }
                        if (mutating.isNotEmpty()) { previewMutatingActions(mutating, activeWorkspace); shouldContinue = false }
                        else if (toolResults.isEmpty()) shouldContinue = false
                        else {
                            providerMessages += AiMessage("assistant", finalText)
                            toolResults.forEach { providerMessages += AiMessage("user", it.asPromptMessage()) }
                            round++
                            updateAssistant(assistantIndex, "Inspecting workspace…")
                        }
                    }
                }
                val current = _messages.value.toMutableList()
                if (assistantIndex < current.size && current[assistantIndex].role == "assistant") current[assistantIndex] = current[assistantIndex].copy(content = NexusActionProtocol.stripProtocol(finalText))
                _messages.value = current
                repository.save(id, current)
                _tokenUsage.value = TokenUsage(finalResponse?.inputTokens ?: estimateTokens(system + providerMessages.joinToString { it.content }), finalResponse?.outputTokens ?: estimateTokens(finalText))
            } catch (cancelled: CancellationException) {
                val current = _messages.value
                if (current.lastOrNull()?.role == "assistant" && current.last().content.isNotBlank()) repository.save(id, current) else repository.save(id, history)
                throw cancelled
            } catch (error: Throwable) {
                _messages.value = history
                _error.value = error.message ?: "AI request failed."
                repository.save(id, history)
            } finally { _generating.value = false }
        }
    }

    private fun updateAssistant(index: Int, content: String) {
        val current = _messages.value.toMutableList()
        if (index < current.size && current[index].role == "assistant") { current[index] = current[index].copy(content = content); _messages.value = current }
    }

    private fun normalizeCommand(input: String): String {
        val command = input.substringBefore(' ').lowercase()
        val body = input.substringAfter(' ', "").trim()
        val instruction = when (command) {
            "/explain" -> "Explain the relevant code clearly and point to the files involved."
            "/fix" -> "Diagnose and fix the problem. Inspect relevant files before proposing changes."
            "/refactor" -> "Refactor the relevant code without changing intended behavior."
            "/optimize" -> "Optimize the relevant code while preserving behavior and correctness."
            "/test" -> "Inspect the code and create or improve appropriate tests."
            "/build" -> "Inspect the project build configuration and determine the correct build workflow."
            "/search" -> "Search the workspace for the requested symbol, file, or implementation."
            "/open" -> "Open the requested workspace file in the editor."
            else -> return input
        }
        return if (body.isBlank()) instruction else "$instruction\nUser request: $body"
    }

    fun stop() { generationJob?.cancel(); generationJob = null; _generating.value = false }
    fun clear() { stop(); workspaceId?.let(repository::clear); _messages.value = emptyList(); _actionProposals.value = emptyList(); _actionReviews.value = emptyMap(); _actionMessage.value = null; _error.value = null; _tokenUsage.value = TokenUsage(); _contextSnapshot.value = null }
    fun clearError() { _error.value = null }
    fun rejectAction(id: String) { _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.REJECTED) else it } }
    fun openAction(id: String) { val target = _actionProposals.value.firstOrNull { it.id == id } ?: return; val path = target.action.path ?: return; if (target.action.type == "open_file" || target.action.type == "focus_file") workspace?.let { NexusEditorActionBus.request(it.id, path, target.action.type == "focus_file") } }

    fun approveAction(id: String) {
        val target = _actionProposals.value.firstOrNull { it.id == id } ?: return
        if (target.status in setOf(NexusActionStatus.REJECTED, NexusActionStatus.COMPLETED, NexusActionStatus.EXECUTING)) return
        val targetWorkspace = workspace ?: run { _actionMessage.value = "Open a workspace before applying this action."; return }
        viewModelScope.launch {
            if (NexusActionPolicy.requiresApproval(target.action)) {
                val review = runCatching { actionExecutor.preview(targetWorkspace, target) }.getOrElse { _actionMessage.value = it.message ?: "Unable to prepare action review."; return@launch }
                if (review != null) {
                    _actionReviews.value = _actionReviews.value + (id to review)
                    if (!review.changed) { _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.COMPLETED) else it }; _actionMessage.value = "No changes to apply for ${review.path}."; return@launch }
                }
            }
            _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = NexusActionStatus.EXECUTING) else it }
            val result = actionExecutor.execute(targetWorkspace, target.action)
            _actionProposals.value = _actionProposals.value.map { if (it.id == id) it.copy(status = if (result.success) NexusActionStatus.COMPLETED else NexusActionStatus.FAILED) else it }
            _actionMessage.value = result.output?.takeIf { it.isNotBlank() } ?: result.message
            if (result.success && (target.action.type == "open_file" || target.action.type == "focus_file")) openAction(id)
            if (result.success && target.action.type in MUTATING_ACTIONS) runCatching { workspaceContextService.refresh(targetWorkspace) }
        }
    }

    private fun estimateTokens(text: String): Int = (text.length + 3) / 4
    override fun onCleared() { generationJob?.cancel(); super.onCleared() }
    companion object {
        private val MUTATING_ACTIONS = setOf("create_file", "create_directory", "patch_file", "replace_file", "delete_file", "rename_file", "copy_file", "move_file")
        private const val WORKSPACE_TOOL_INSTRUCTIONS = """
You are Nexus, an AI software-engineering agent operating inside the user's currently opened workspace.
Inspect the workspace through Nexus actions before making assumptions. Never claim a file was changed unless Nexus actually executed the action.
Emit actions exactly as <nexus-action>{JSON}</nexus-action>.
Supported actions: list_files, read_file, open_file, focus_file, create_file, create_directory, patch_file, replace_file, delete_file, rename_file, copy_file, move_file.
Read/list/open/focus are automatically executed and their results are returned to you. Mutating actions require explicit user approval.
Use relative paths only. Never use absolute paths or '..'. Prefer patch_file for existing code so the user can review a diff.
"""
    }
}

data class TokenUsage(val input: Int = 0, val output: Int = 0) { val total: Int get() = input + output }
