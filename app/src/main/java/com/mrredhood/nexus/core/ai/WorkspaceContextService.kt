package com.mrredhood.nexus.core.ai

import com.mrredhood.nexus.core.workspace.Workspace
import com.mrredhood.nexus.core.workspace.WorkspaceFileSystem
import com.mrredhood.nexus.core.workspace.WorkspaceIndexService
import com.mrredhood.nexus.core.workspace.WorkspaceIntelligenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds AI-ready workspace context from the shared workspace index. */
class WorkspaceContextService(private val fileSystem: WorkspaceFileSystem) {
    private val intelligenceService = WorkspaceIntelligenceService(WorkspaceIndexService(fileSystem))

    suspend fun refresh(workspace: Workspace): String = withContext(Dispatchers.Default) {
        val intelligence = intelligenceService.analyze(workspace)
        val context = buildString {
            appendLine("WORKSPACE: ${workspace.displayName}")
            appendLine(intelligence.toContextText())
            appendLine()
            appendLine("INDEXED FILES:")
            intelligence.index.files
                .sortedBy { it.path }
                .take(MAX_CONTEXT_FILES)
                .forEach { entry ->
                    val symbols = if (entry.symbols.isEmpty()) "" else " | symbols: ${entry.symbols.take(MAX_SYMBOLS_PER_FILE).joinToString(", ") }"
                    appendLine("- ${entry.path} | ${entry.language} | ${entry.sizeBytes} bytes$symbols")
                }
        }.trim()

        ChatContextStore.updateWorkspaceSummary(workspace.id, context)
        context
    }

    companion object {
        private const val MAX_CONTEXT_FILES = 120
        private const val MAX_SYMBOLS_PER_FILE = 12
    }
}
