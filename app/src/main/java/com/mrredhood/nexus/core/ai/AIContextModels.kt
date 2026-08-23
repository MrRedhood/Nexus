package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.WorkspaceRetrievalResult

/** Controls which context sources are allowed into an AI request. */
data class AIContextOptions(
    val automaticContext: AutomaticContextMode = AutomaticContextMode.SMART,
    val maxRelatedFiles: Int = 5,
    val maxFileSizeChars: Int = 40_000,
    val maxContextTokens: Int = 32_000,
    val includeCurrentFile: Boolean = true,
    val includeSelection: Boolean = true,
    val includeGitDiff: Boolean = true,
    val includeTerminalOutput: Boolean = true,
    val includeWorkspaceSummary: Boolean = true,
    val includeMemory: Boolean = true
) {
    init {
        require(maxRelatedFiles in 0..50)
        require(maxFileSizeChars in 1..500_000)
        require(maxContextTokens in 256..1_000_000)
    }
}

enum class AutomaticContextMode { NEVER, SMART, ALWAYS }

enum class AIContextSource {
    USER_MESSAGE,
    SELECTION,
    CURRENT_FILE,
    REFERENCED_FILE,
    RELATED_FILE,
    GIT_DIFF,
    TERMINAL_OUTPUT,
    WORKSPACE_SUMMARY,
    MEMORY
}

data class AIContextItem(
    val source: AIContextSource,
    val label: String,
    val path: String? = null,
    val content: String,
    val estimatedTokens: Int,
    val included: Boolean = true,
    val reason: String? = null
)

data class AIContextSnapshot(
    val items: List<AIContextItem>,
    val estimatedTokens: Int,
    val tokenLimit: Int,
    val truncated: Boolean,
    val droppedItems: List<AIContextItem> = emptyList()
) {
    val includedItems: List<AIContextItem> get() = items.filter { it.included }

    fun asPromptContext(): String = includedItems.joinToString("\n\n") { item ->
        val header = when (item.source) {
            AIContextSource.USER_MESSAGE -> "USER REQUEST"
            AIContextSource.SELECTION -> "SELECTION: ${item.label}"
            AIContextSource.CURRENT_FILE -> "CURRENT FILE: ${item.path ?: item.label}"
            AIContextSource.REFERENCED_FILE -> "REFERENCED FILE: ${item.path ?: item.label}"
            AIContextSource.RELATED_FILE -> "RELATED FILE: ${item.path ?: item.label}"
            AIContextSource.GIT_DIFF -> "GIT DIFF"
            AIContextSource.TERMINAL_OUTPUT -> "TERMINAL OUTPUT"
            AIContextSource.WORKSPACE_SUMMARY -> "WORKSPACE SUMMARY"
            AIContextSource.MEMORY -> "MEMORY"
        }
        "[$header]\n${item.content}"
    }
}

data class AIContextRequest(
    val userMessage: String,
    val currentFile: AIContextItem? = null,
    val selection: AIContextItem? = null,
    val referencedFiles: List<AIContextItem> = emptyList(),
    val relatedFiles: List<Pair<WorkspaceRetrievalResult, AIContextItem>> = emptyList(),
    val gitDiff: AIContextItem? = null,
    val terminalOutput: AIContextItem? = null,
    val workspaceSummary: AIContextItem? = null,
    val memory: AIContextItem? = null
)
